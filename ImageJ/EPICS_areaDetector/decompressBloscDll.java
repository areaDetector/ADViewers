import java.nio.Buffer;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;

public class decompressBloscDll {

	static {
		Native.register("blosc" + getArchPlatform());
	}

	public static String getArchPlatform() {
		String archDataModel = System.getProperty("sun.arch.data.model");
		if (archDataModel.equals("64")) {
			archDataModel = "";
		}
		return archDataModel;
	}

  public static native int blosc_decompress(Buffer src, Buffer dest, NativeLong destSize);
}
