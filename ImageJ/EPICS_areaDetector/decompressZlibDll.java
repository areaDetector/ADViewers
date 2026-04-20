import java.nio.Buffer;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.ptr.NativeLongByReference;

public class decompressZlibDll {

	static {
		try {
			Native.register("z" + getArchPlatform());
		} catch (UnsatisfiedLinkError e) {
			Native.register("zlib" + getArchPlatform());
		}
	}

	public static String getArchPlatform() {
		String archDataModel = System.getProperty("sun.arch.data.model");
		if (archDataModel.equals("64")) {
			archDataModel = "";
		}
		return archDataModel;
	}

	public static native int uncompress(Buffer dest, NativeLongByReference destLen, Buffer src, NativeLong srcLen);
}
