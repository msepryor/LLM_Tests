package com.matt.llminterfaces;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class ImageResponse extends ResponseItem {
	
	byte [] binaryData;
	String dataType;
	String mediaType;
	String accompanyingInputText;
	
	public byte[] getBinaryData() {
		return binaryData;
	}
	public void setBinaryData(byte[] binaryData) {
		this.binaryData = binaryData;
	}
	public String getDataType() {
		return dataType;
	}
	public void setDataType(String dataType) {
		this.dataType = dataType;
	}
	public String getMediaType() {
		return mediaType;
	}
	public void setMediaType(String mediaType) {
		this.mediaType = mediaType;
	}
	
	public String toBase64() {
		String base64 = new String (Base64.getEncoder().encode(getBinaryData()), StandardCharsets.UTF_8);
		return base64;
	}
	
	public String getAccompanyingInputText() {
		return accompanyingInputText;
	}
	public void setAccompanyingInputText(String accompanyingInputText) {
		this.accompanyingInputText = accompanyingInputText;
	}	
	

}
