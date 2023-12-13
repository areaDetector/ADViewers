import java.nio.Buffer;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;

public class decompressH264Dll {

	static {
		Native.register("videoCompression");
	}

	public static String getArchPlatform() {
		String archDataModel = System.getProperty("sun.arch.data.model");
                System.out.println(archDataModel);
		if (archDataModel.equals("64")) {
			archDataModel = "";
		}
		return archDataModel;
	}

	public static native void H264_decompress(Buffer src, Buffer dest, NativeLong destSize);
}
