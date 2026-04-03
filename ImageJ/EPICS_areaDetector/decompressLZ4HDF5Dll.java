import java.nio.Buffer;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;

public class decompressLZ4HDF5Dll {

	static {
		Native.register("lz4hdf5" + getArchPlatform());
	}

	public static String getArchPlatform() {
		String archDataModel = System.getProperty("sun.arch.data.model");
		if (archDataModel.equals("64")) {
			archDataModel = "";
		}
		return archDataModel;
	}

	public static native void decompress_lz4hdf5(Buffer src, Buffer dest, NativeLong destSize);
}
