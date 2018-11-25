import java.nio.Buffer;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;

public class decompressJPEGDll {

	static {
		Native.register("decompressJPEG" + getArchPlatform());
	}

	public static String getArchPlatform() {
		String archDataModel = System.getProperty("sun.arch.data.model");
		if (archDataModel.equals("64")) {
			archDataModel = "";
		}
		return archDataModel;
	}

	public static native void decompressJPEG(Buffer src, NativeLong srcSize, Buffer dest, NativeLong destSize);

}
