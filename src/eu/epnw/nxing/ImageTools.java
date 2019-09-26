package eu.epnw.nxing;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

public class ImageTools {
	
	public static String decodeQRCode(ByteBuf qrCodeimage) throws IOException, NotFoundException, DataFormatException {
		if(isJpgOrPng(qrCodeimage)) {
		InputStream in=new ByteBufInputStream(qrCodeimage);
		BufferedImage bufferedImage = ImageIO.read(in);
		LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		Result result = new MultiFormatReader().decode(bitmap);
		return result.getText();
		} else {
			throw new DataFormatException("Not a jpg or png!");
		}
	}

	private static boolean isJpgOrPng(ByteBuf buf) throws IOException {
		return isJpg(buf) || isPng(buf);		}

	private static boolean isJpg(ByteBuf buf) {
		boolean b = false;
		buf.markReaderIndex();
		if (buf.readByte() == (byte) 0xFF && buf.readByte() == (byte) 0xD8 && buf.readByte() == (byte) 0xFF) {
			b = true;
		} else {
			b = false;
		}
		buf.resetReaderIndex();
		return b;
	}

	private static boolean isPng(ByteBuf buf) {
		boolean b = false;
		buf.markReaderIndex();
		if (buf.readByte() == (byte) 0x89 && buf.readByte() == (byte) 0x50 && buf.readByte() == (byte) 0x4E
				&& buf.readByte() == (byte) 0x47 && buf.readByte() == (byte) 0x0D && buf.readByte() == (byte) 0x0A
				&& buf.readByte() == (byte) 0x1A && buf.readByte() == (byte) 0x0A) {
			b = true;
		} else {
			b = false;
		}
		buf.resetReaderIndex();
		return b;
	}
}
