/*
 *  Extension to leshan-server-demo for application code.
 */

package org.course;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.swing.plaf.DimensionUIResource;

import java.util.Set;
import java.io.PrintWriter;

import org.eclipse.leshan.server.californium.LeshanServer;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.core.response.WriteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.WriteRequest.Mode;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.observation.SingleObservation;


public class RoomControl {
    
    //
    // Static reference to the server.
    //
    private static LeshanServer lwServer;

    //
    // 2IMN15:  TODO  : fill in
    //
    // Declare variables to keep track of the state of the room.

    private static int usedPeakPower; // Sum of peak powers of all luminaires
    private static int maxPeakPower; // Maximum peak power given by demand response
    // Peak power map, maps endpoint to peak power of a luminaire
    private static Map<String, Integer> luminairePowers = new HashMap<>();
    
    public static void Initialize(LeshanServer server)
    {
	// Register the LWM2M server object for future use
	lwServer = server;

	// 2IMN15:  TODO  : fill in
	//
	// Initialize the state variables.

        // No clients have been registered, therefore
        // these variables should be 0 at constructor
        usedPeakPower = 0;
        maxPeakPower = 0;

    }

    //
    // Suggested support methods:
    //
    // * set the dim level of all luminaires.
    // * set the power flag of all luminaires.
    // * show the status of the room.
    //
    // Support methods: 

    /**
     * Given the current sum of peak power of all luminaires and the current power budget, 
     * calculate the best dim level, and set all luminaires' dim level to that value. 
     *
     * @param powerBudget -> currently allowed peak power given by demand response
     */
    private static void calculateAndDimLuminaires (int powerBudget) {
        // Calculate best dim level given budget and peak power
        int newDimLevel = (int) ((double) powerBudget / (double) usedPeakPower * 100);
        // Bound the dime level by 100. 0 bound not necessary unless faulty input
        newDimLevel = newDimLevel > 100 ? 100 : newDimLevel;
        // For each registered luminaire, set its dim level to the new value
        for (String endPoint : luminairePowers.keySet()) {
            Registration reg = lwServer.getRegistrationService().getByEndpoint(endPoint);
            writeInteger(reg, Constants.LUMINAIRE_ID, 0, Constants.RES_DIM_LEVEL, newDimLevel);
        }
    }

    /**
     * Given the current sum of peak power of all luminaires and the current power budget, 
     * calculate the best dim level, and set all luminaires' dim level to that value. 
     *
     * @param observation, a SingleObservation instanced linked to the observed object
     * @param response, an ObserveResponse contraining update details from the observed object
     * @return whether presence is detected, null if response not from the presence detector
     */
    private static Boolean observedPresence (SingleObservation observation, 
        ObserveResponse response) 
    {
        // Obtain path that identifies type of object observed
        LwM2mPath obsPath = observation.getPath();
        // Check if the response came from the presence detector
        if ((obsPath.getObjectId() == Constants.PRESENCE_DETECTOR_ID) &&
        (obsPath.getResourceId() == Constants.RES_PRESENCE)) {
            // Get response string
            String strValue = ((LwM2mResource)response.getContent()).getValue().toString();
            try {
                // Convert response string to boolean denoting presence
                boolean vPresence = Boolean.parseBoolean(strValue);
                return vPresence;
            }
            catch (Exception e) {
                System.out.println("Exception in reading presence state:" + e.getMessage());
            }
        }
        return null; // Return null if response not from the presence detector
    }

    public static void handleRegistration(Registration registration)
    {
        // Check which objects are available.
        Map<Integer,org.eclipse.leshan.core.LwM2m.Version> supportedObject =
	    registration.getSupportedObject();

        if (supportedObject.get(Constants.PRESENCE_DETECTOR_ID) != null) {
	    System.out.println("Presence Detector");

	    //
	    // 2IMN15:  TODO  :  fill in
	    //
	    // Process the registration of a new Presence Detector.

            // Observe the presence state information for updates,
            // Same as how the default code observes the demand response object.
            try {
                ObserveRequest obRequest =
                    new ObserveRequest(Constants.PRESENCE_DETECTOR_ID,
                        0,
                        Constants.RES_PRESENCE);
                System.out.println(">>ObserveRequest for presence created << ");
                ObserveResponse coResponse = lwServer.send(registration, obRequest, 1000);
                System.out.println(">>ObserveRequest for presence sent << ");
                if (coResponse == null) {
                    System.out.println(">>ObserveRequest for presence null << ");
                }
            } catch (Exception e) {
                System.out.println("Observe request for presence failed for Presence Detector.");
            }
        }

        if (supportedObject.get(Constants.LUMINAIRE_ID) != null) {
	    System.out.println("Luminaire");

	    //
	    // 2IMN15:  TODO  :  fill in
	    //
	    // Process the registration of a new Luminaire.

            // Get peak power of the registered luminaire
            int lumPeakPower = readInteger(
                    registration, 
                    Constants.LUMINAIRE_ID,
                    0,
                    Constants.RES_PEAK_POWER);

            // Get type of the registered luminaire
            String lumType = readString(
                    registration, 
                    Constants.LUMINAIRE_ID,
                    0,
                    Constants.RES_TYPE);

            // Ensure only allowed type of Luminaires are added
            if (lumType.equals("LED") || lumType.equals("Halogen")) {
                // Add peak power to used Peak Power
                usedPeakPower += lumPeakPower;
                // Add peak power to peak power map
                luminairePowers.put(registration.getEndpoint(), lumPeakPower);
                // Adding a new luminaire must not break the budget
                calculateAndDimLuminaires(maxPeakPower);

                System.out.println("Registered Luminaire with Peak Power: " + lumPeakPower);

            // In case of unknown Luminaire type
            } else {
                System.out.println("Unidentified Luminaire type: " + lumType);
            }

        }

        if (supportedObject.get(Constants.DEMAND_RESPONSE_ID) != null) {
	    System.out.println("Demand Response");
	    //
	    // The registerDemandResponse() method contains example code
	    // on how handle a registration. 
	    //
            int powerBudget = registerDemandResponse(registration);
            // Track the power budget given by demand response
            maxPeakPower = powerBudget;
            // Calculate appropriate dim level and dim the luminaires
            calculateAndDimLuminaires(maxPeakPower);
        }

	//  2IMN15: don't forget to update the other luminaires.
    }
    

    public static void handleDeregistration(Registration registration)
    {
	//
	// 2IMN15:  TODO  :  fill in
	//
	// The device identified by the given registration will
	// disappear.  Update the state accordingly.

        // Get peak power of de-registered luminair
        Integer freedPower = luminairePowers.get(registration.getEndpoint());
        // Verify that a luminaire is deregistred
        if (freedPower != null) {
            // Subtract from used peak power and remove luminair from peak power map
            usedPeakPower -= freedPower;
            luminairePowers.remove(registration.getEndpoint());
        }
    }
    
    public static void handleObserveResponse(SingleObservation observation,
					     Registration registration,
					     ObserveResponse response)
    {
        if (registration != null && observation != null && response != null) {
	    //
	    // 2IMN15:  TODO  :  fill in
	    //
	    // When the registration and observation are known,
	    // process the value contained in the response.
	    //
	    // Useful methods:
	    //    registration.getEndpoint()
	    //    observation.getPath()
	    
            // Reponse is from either presence detector or demand resopnse
            Boolean vPresence = observedPresence(observation, response);
            int newPowerBudget = observedDemandResponse(observation, response);

            // If response is from presence detector
            if (vPresence != null && newPowerBudget == -1) {
                // Set all luminaire's power status to the presense status
                // Prenses: true -> Power: true
                // Prenses: false -> Power: false
                for (String endPoint : luminairePowers.keySet()) {
                    Registration reg = lwServer.getRegistrationService().getByEndpoint(endPoint);
                    writeBoolean(reg, Constants.LUMINAIRE_ID, 0, Constants.RES_POWER, vPresence);
                }
            // If response is from demand response
            } else if (vPresence == null && newPowerBudget != -1) {
                // Calculate appropriate dim level and update all luminaires
                calculateAndDimLuminaires(newPowerBudget);
            }
        }
    }


    // Support functions for reading and writing resources of
    // certain types.

    // Returns the current power budget.
    private static int registerDemandResponse(Registration registration)
    {
	int powerBudget = readInteger(registration,
				      Constants.DEMAND_RESPONSE_ID,
				      0,
				      Constants.RES_TOTAL_BUDGET);
	System.out.println("Power budget is " + powerBudget);
	// Observe the total budget information for updates.
	try {
	    ObserveRequest obRequest =
		new ObserveRequest(Constants.DEMAND_RESPONSE_ID,
				   0,
				   Constants.RES_TOTAL_BUDGET);
	    System.out.println(">>ObserveRequest created << ");
	    ObserveResponse coResponse =
		lwServer.send(registration, obRequest, 1000);
	    System.out.println(">>ObserveRequest sent << ");
	    if (coResponse == null) {
		System.out.println(">>ObserveRequest null << ");
	    }
	}
	catch (Exception e) {
	    System.out.println("Observe request failed for Demand Response.");
	}
	return powerBudget;
    }

    // If the response contains a new power budget, it returns that value.
    // Otherwise, it returns -1.
    private static int observedDemandResponse(SingleObservation observation,
					      ObserveResponse response)
    {
	// Alternative code:
	// String obsRes = observation.getPath().toString();
	// if (obsRes.equals("/33002/0/30005")) 
	LwM2mPath obsPath = observation.getPath();
	if ((obsPath.getObjectId() == Constants.DEMAND_RESPONSE_ID) &&
	    (obsPath.getResourceId() == Constants.RES_TOTAL_BUDGET)) {
	    String strValue = ((LwM2mResource)response.getContent()).getValue().toString();
	    try {
		int newPowerBudget = Integer.parseInt(strValue);

		return newPowerBudget;
	    }
	    catch (Exception e) {
		System.out.println("Exception in reading demand response:" + e.getMessage());
	    }	       
	}
	return -1;
    }
    
    
    private static int readInteger(Registration registration, int objectId, int instanceId, int resourceId)
    {
        try {
	    ReadRequest request = new ReadRequest(objectId, instanceId, resourceId);
	    ReadResponse cResponse = lwServer.send(registration, request, 5000);
	    if (cResponse.isSuccess()) {
		String sValue = ((LwM2mResource)cResponse.getContent()).getValue().toString();
		try {
		    int iValue = Integer.parseInt(((LwM2mResource)cResponse.getContent()).getValue().toString());
		    return iValue;
		}
		catch (Exception e) {
		}
		float fValue = Float.parseFloat(((LwM2mResource)cResponse.getContent()).getValue().toString());
		return (int)fValue;
	    } else {
		return 0;
	    }
        }
        catch (Exception e) {
	    System.out.println(e.getMessage());
	    System.out.println("readInteger: exception");
	    return 0;
        }
    }
    
    private static String readString(Registration registration, int objectId, int instanceId, int resourceId)
    {
        try {
	    ReadRequest request = new ReadRequest(objectId, instanceId, resourceId);
	    ReadResponse cResponse = lwServer.send(registration, request, 1000);
	    if (cResponse.isSuccess()) {
		String value = ((LwM2mResource)cResponse.getContent()).getValue().toString();
		return value;
	    } else {
		return "";
	    }
        }
        catch (Exception e) {
	    System.out.println(e.getMessage());
	    System.out.println("readString: exception");
	    return "";
        }
    }
    
    private static void writeInteger(Registration registration, int objectId, int instanceId, int resourceId, int value)
    {
	try {
	    WriteRequest request = new WriteRequest(objectId, instanceId, resourceId, value);
	    WriteResponse cResponse = lwServer.send(registration, request, 1000);
	    if (cResponse.isSuccess()) {
		System.out.println("writeInteger: Success");
	    } else {
		System.out.println("writeInteger: Failed, " + cResponse.toString());
	    }
	}
	catch (Exception e) {
	    System.out.println(e.getMessage());
	    System.out.println("writeInteger: exception");
	}
    }
    
    private static void writeString(Registration registration, int objectId, int instanceId, int resourceId, String value)
    {
	try {
	    WriteRequest request = new WriteRequest(objectId, instanceId, resourceId, value);
	    WriteResponse cResponse = lwServer.send(registration, request, 1000);
	    if (cResponse.isSuccess()) {
		System.out.println("writeString: Success");
	    } else {
		System.out.println("writeString: Failed, " + cResponse.toString());
	    }
	}
	catch (Exception e) {
	    System.out.println(e.getMessage());
	    System.out.println("writeString: exception");
	}
    }
    
    private static void writeBoolean(Registration registration, int objectId, int instanceId, int resourceId, boolean value)
    {
	try {
	    WriteRequest request = new WriteRequest(objectId, instanceId, resourceId, value);
	    WriteResponse cResponse = lwServer.send(registration, request, 1000);
	    if (cResponse.isSuccess()) {
		System.out.println("writeBoolean: Success");
	    } else {
		System.out.println("writeBoolean: Failed, " + cResponse.toString());
	    }
	}
	catch (Exception e) {
	    System.out.println(e.getMessage());
	    System.out.println("writeBoolean: exception");
	}
    }
    
}
