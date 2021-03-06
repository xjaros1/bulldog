package io.silverspoon.bulldog.linux.gpio;

import io.silverspoon.bulldog.core.Edge;
import io.silverspoon.bulldog.core.Signal;
import io.silverspoon.bulldog.core.gpio.Pin;
import io.silverspoon.bulldog.core.gpio.base.AbstractDigitalInput;
import io.silverspoon.bulldog.core.gpio.event.InterruptEventArgs;
import io.silverspoon.bulldog.core.gpio.event.InterruptListener;
import io.silverspoon.bulldog.linux.io.LinuxEpollListener;
import io.silverspoon.bulldog.linux.io.LinuxEpollThread;
import io.silverspoon.bulldog.linux.jni.NativePollResult;
import io.silverspoon.bulldog.linux.sysfs.SysFsPin;

public class LinuxDigitalInput extends AbstractDigitalInput implements LinuxEpollListener {

   private LinuxEpollThread interruptControl;
   private SysFsPin sysFsPin;
   private Edge lastEdge;
   private volatile long lastInterruptTime;

   public LinuxDigitalInput(Pin pin) {
      super(pin);
      sysFsPin = createSysFsPin(getPin());
      interruptControl = new LinuxEpollThread(sysFsPin.getValueFilePath().toString());
      interruptControl.addListener(this);
   }

   protected SysFsPin createSysFsPin(Pin pin) {
      return new SysFsPin(pin.getAddress());
   }

   public Signal read() {
      return sysFsPin.getValue();
   }

   @Override
   public void addInterruptListener(InterruptListener listener) {
      super.addInterruptListener(listener);
      if (areInterruptsEnabled() && !interruptControl.isRunning()) {
         interruptControl.start();
      }
   }

   @Override
   public void removeInterruptListener(InterruptListener listener) {
      super.removeInterruptListener(listener);
      if (getInterruptListeners().size() == 0) {
         interruptControl.stop();
      }
   }

   @Override
   public void clearInterruptListeners() {
      super.clearInterruptListeners();
      interruptControl.stop();
   }

   protected void enableInterruptsImpl() {
      if (getInterruptListeners().size() > 0 && !interruptControl.isRunning()) {
         interruptControl.start();
      }
   }

   protected void disableInterruptsImpl() {
      interruptControl.teardown();
   }

   @Override
   protected void setupImpl() {
      exportPinIfNecessary();
      interruptControl.setup();
   }

   @Override
   protected void teardownImpl() {
      disableInterrupts();
      unexportPin();
   }

   protected void exportPinIfNecessary() {
      sysFsPin.exportIfNecessary();
      sysFsPin.setDirection("in");
      sysFsPin.setEdge(getInterruptTrigger().toString().toLowerCase());
   }

   protected void unexportPin() {
      sysFsPin.unexport();
   }

   @Override
   public void setInterruptTrigger(Edge edge) {
      super.setInterruptTrigger(edge);
      sysFsPin.setEdge(getInterruptTrigger().toString().toLowerCase());
   }

   @Override
   public void processEpollResults(NativePollResult[] results) {
      for (NativePollResult result : results) {
         Edge edge = getEdge(result);
         if (lastEdge != null && lastEdge.equals(edge)) {
            continue;
         }

         long delta = System.currentTimeMillis() - lastInterruptTime;
         if (delta <= this.getInterruptDebounceMs()) {
            continue;
         }

         lastInterruptTime = System.currentTimeMillis();
         lastEdge = edge;
         fireInterruptEvent(new InterruptEventArgs(getPin(), edge));
      }
   }

   private Edge getEdge(NativePollResult result) {
      if (result.getData() == null) {
         return null;
      }
      if (result.getDataAsString().charAt(0) == '1') {
         return Edge.Rising;
      }

      return Edge.Falling;
   }

   public SysFsPin getSysFsPin() {
      return sysFsPin;
   }
}
