package org.fog.utils;

import java.util.HashMap;
import java.util.Map;

import org.fog.entities.FogDevice;

public class FogUtils {
	private static int TUPLE_ID = 1;
	private static int ENTITY_ID = 1;
	private static int ACTUAL_TUPLE_ID = 1;
	
	public static int generateTupleId(){
		return TUPLE_ID++;
	}
	
	public static String getSensorTypeFromSensorName(String sensorName){
		return sensorName.substring(sensorName.indexOf('-')+1, sensorName.lastIndexOf('-'));
	}
	
	public static int generateEntityId(){
		return ENTITY_ID++;
	}
	
	public static int generateActualTupleId(){
		return ACTUAL_TUPLE_ID++;
	}
	
	public static int USER_ID = 1;
	
	//public static int MAX = 10000000;
	public static int MAX = 10000000;
	
	public static Map<String, GeoCoverage> appIdToGeoCoverageMap = new HashMap<String, GeoCoverage>();

	public static FogDevice createFogDevice(String name, long mips, int ram, long upBw, long downBw, int level,
			double ratePerMips, double busyPower, double idlePower) {
		// TODO Auto-generated method stub
		return null;
	}
}
