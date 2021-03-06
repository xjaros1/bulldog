package io.silverspoon.bulldog.beagleboneblack;

import io.silverspoon.bulldog.core.platform.Board;
import io.silverspoon.bulldog.core.platform.BoardFactory;
import io.silverspoon.bulldog.linux.sysinfo.CpuInfo;
import io.silverspoon.bulldog.linux.util.LinuxLibraryLoader;

public class BeagleBoneBlackBoardFactory implements BoardFactory {

   @Override
   public boolean isCompatibleWithPlatform() {
      return CpuInfo.getHardware().contains("AM33XX"); 
   }

   @Override
   public Board createBoard() {
      LinuxLibraryLoader.loadNativeLibrary("beagleboneblack");
      return new BeagleBoneBlack();
   }

}
