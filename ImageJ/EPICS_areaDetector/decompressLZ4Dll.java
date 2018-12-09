import java.nio.Buffer;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;

public class decompressLZ4Dll {

	static {
		Native.register("bitshuffle" + getArchPlatform());
	}

	public static String getArchPlatform() {
		String archDataModel = System.getProperty("sun.arch.data.model");
		if (archDataModel.equals("64")) {
			archDataModel = "";
		}
		return archDataModel;
	}

	public static native void LZ4_decompress_fast(Buffer src, Buffer dest, NativeLong destSize);
}
