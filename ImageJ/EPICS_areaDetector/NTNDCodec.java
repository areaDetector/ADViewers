// EPICS_NTNDA_Viewer.java
// Original authors
//      Adapted for EPICS V4 from EPICS_CA_Viewer by Tim Madden
//      Tim Madden, APS
//      Mark Rivers, University of Chicago
//      Marty Kraimer
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.blosc.JBlosc;
import org.epics.nt.NTNDArray;
import org.epics.pvdata.factory.BasePVByteArray;
import org.epics.pvdata.factory.BasePVDoubleArray;
import org.epics.pvdata.factory.BasePVFloatArray;
import org.epics.pvdata.factory.BasePVIntArray;
import org.epics.pvdata.factory.BasePVShortArray;
import org.epics.pvdata.factory.BasePVUByteArray;
import org.epics.pvdata.factory.BasePVUIntArray;
import org.epics.pvdata.factory.BasePVUShortArray;
import org.epics.pvdata.factory.BaseScalarArray;
import org.epics.pvdata.factory.ConvertFactory;
import org.epics.pvdata.pv.Convert;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVScalarArray;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.PVUnion;
import org.epics.pvdata.pv.ScalarType;

import com.sun.jna.NativeLong;

/**
 * Codec processor for an NTNDArray
 *
 */

public class NTNDCodec 
{
    private static Convert convert = ConvertFactory.getConvert();
    private JBlosc jBlosc = null;
    private static final int INITIAL_BLOSC_SIZE = 10 * 1024 * 1024;
    private ByteBuffer decompressInBuffer = ByteBuffer.allocateDirect(INITIAL_BLOSC_SIZE);
    private ByteBuffer decompressOutBuffer = ByteBuffer.allocateDirect(INITIAL_BLOSC_SIZE);
    private byte[] compressedArray = new byte[INITIAL_BLOSC_SIZE];
    private String message;
    /**
     * Constructor
     */
    public NTNDCodec()
    {
    }
    
    /**
     * Get the error message if other methods return false;
     * @return The last error message.
     */
    public String getMessage()
    {
        return message;
    }
    /**
     * decompress a compressed array.
     * @param ntndArray The NTNDArray that holds a possibly compressed array.
     * @return (false,true) if (failure, success).
     * If success the original value holds the result.
     * If failure getMessage will return the reason.
     */
    public boolean decompress(NTNDArray ntndArray)
    {
        PVStructure pvs = ntndArray.getPVStructure();
        PVStructure pvCodec = ntndArray.getCodec();
        String codecName = pvCodec.getSubField(PVString.class, "name").get();
        if(codecName.isEmpty()) return true;

        PVUnion pvUnionValue = pvs.getSubField(PVUnion.class,"value");
        if(pvUnionValue==null) {
            message = "value not found";
            return false;
        }
        PVScalarArray imagedata = pvUnionValue.get(PVScalarArray.class);
        if(imagedata==null) {
            message = "value is not a scalar array";
            return false;
        }
        int numElements = imagedata.getLength();
        if (numElements == 0) {
            message = "array size = 0";
            return false;
        }
        int compressedSize = (int)ntndArray.getCompressedDataSize().get();
        int uncompressedSize = (int)ntndArray.getCompressedDataSize().get();
        PVUnion pvCodecParamUnion = pvCodec.getSubField(PVUnion.class, "parameters");
        PVInt pvCodecParams = pvCodecParamUnion.get(PVInt.class);
        int decompressedDataType = pvCodecParams.get();
        ScalarType scalarType  = ScalarType.values()[decompressedDataType];
        if (decompressInBuffer.capacity() < compressedSize) {
            decompressInBuffer = ByteBuffer.allocateDirect((int)uncompressedSize);
        }
        if (decompressOutBuffer.capacity() < uncompressedSize) {
            decompressOutBuffer = ByteBuffer.allocateDirect((int)uncompressedSize);
        }
        if (compressedArray.length < compressedSize) {
            compressedArray = new byte[compressedSize];
        }
        decompressInBuffer.order(ByteOrder.nativeOrder());
        decompressOutBuffer.order(ByteOrder.nativeOrder());
        convert.toByteArray(imagedata, 0, compressedSize, compressedArray, 0);
        decompressInBuffer.put(compressedArray);
        decompressInBuffer.position(0);
        decompressOutBuffer.position(0);
        if (codecName.equals("blosc")) {
            if (jBlosc == null) {
                jBlosc = new JBlosc();
            }
            int status = jBlosc.decompress(decompressInBuffer, decompressOutBuffer, uncompressedSize);
            if (status != uncompressedSize) {
                message = "jBlosc.decompress returned status="+status;
                return false;
            }
            if (scalarType==ScalarType.pvByte) {            
                byte[] temp = new byte[numElements];
                decompressOutBuffer.get(temp);
                BasePVByteArray pvArray = new BasePVByteArray(new BaseScalarArray(scalarType));
                pvArray.put(0, numElements, temp, 0);
                pvUnionValue.set("byteValue", pvArray);
            } else if (scalarType==ScalarType.pvUByte) {            
                byte[] temp = new byte[numElements];
                decompressOutBuffer.get(temp);
                BasePVUByteArray pvArray = new BasePVUByteArray(new BaseScalarArray(scalarType));
                pvArray.put(0, numElements, temp, 0);
                pvUnionValue.set("ubyteValue", pvArray);
            } else if (scalarType==ScalarType.pvShort) {
                short temp[] = myUtil.byteBufferToShortArray(decompressOutBuffer);
                BasePVShortArray pvArray = new BasePVShortArray(new BaseScalarArray(scalarType));
                pvArray.put(0, numElements, temp, 0);
                pvUnionValue.set("shortValue", pvArray);
            } else if (scalarType==ScalarType.pvUShort) {
                short temp[] = myUtil.byteBufferToShortArray(decompressOutBuffer);
                BasePVUShortArray pvArray = new BasePVUShortArray(new BaseScalarArray(scalarType));
                pvArray.put(0, numElements, temp, 0);
                pvUnionValue.set("ushortValue", pvArray);
            } else if (scalarType==ScalarType.pvInt) {
                int temp[] = myUtil.byteBufferToIntArray(decompressOutBuffer);
                BasePVIntArray pvArray = new BasePVIntArray(new BaseScalarArray(scalarType));
                pvArray.put(0, numElements, temp, 0);
                pvUnionValue.set("intValue", pvArray);
            } else if (scalarType==ScalarType.pvUInt) {
                int temp[] = myUtil.byteBufferToIntArray(decompressOutBuffer);
                BasePVUIntArray pvArray = new BasePVUIntArray(new BaseScalarArray(scalarType));
                pvArray.put(0, numElements, temp, 0);
                pvUnionValue.set("uintValue", pvArray);
            } else if (scalarType==ScalarType.pvFloat) {
                float temp[] = myUtil.byteBufferToFloatArray(decompressOutBuffer);
                BasePVFloatArray pvArray = new BasePVFloatArray(new BaseScalarArray(scalarType));
                pvArray.put(0, numElements, temp, 0);
                pvUnionValue.set("floatValue", pvArray);
            } else if (scalarType==ScalarType.pvDouble) {
                double temp[] = myUtil.byteBufferToDoubleArray(decompressOutBuffer);
                BasePVDoubleArray pvArray = new BasePVDoubleArray(new BaseScalarArray(scalarType));
                pvArray.put(0, numElements, temp, 0);
                pvUnionValue.set("doubleValue", pvArray);
            }

        } else if (codecName.equals("jpeg")) {
            if (scalarType==ScalarType.pvUByte) {            
                decompressJPEGDll.decompressJPEG(
                        decompressInBuffer,
                        new NativeLong(compressedSize),
                        decompressOutBuffer,
                        new NativeLong(numElements));
                byte[] temp = new byte[numElements];
                decompressOutBuffer.get(temp);
                convert.fromByteArray(imagedata, 0, numElements, temp, 0);
            } else {
                message = "JPEG Compression not supported for ScalerType="+scalarType;
                return false;
            }
        } else {
            message = "Unknown compression=" +codecName
                   + " compressedSize=" + compressedSize 
                   + " uncompressedSize=" + uncompressedSize;
            return false;
        }
        return true;
    }
}
