// Based on thoward1234's work at
//  https://community.smartthings.com/t/securifi-flood-sensor-device-type/24980, itself based on
//  definitions by Serge Sozonoff


metadata {
	definition (name: "Securifi Water Sensor", namespace: "wesgurn", author: "wesgurn") {
		capability "Water Sensor"
		capability "Sensor"
        capability "Configuration"

        command "enrollResponse"

        fingerprint inClusters: "0000,0001,0003,0402,0500,0020,0B05", outClusters: "0019", manufacturer: "Sercomm Corp.", model: "SZ-WTD02N_SF", deviceJoinName: "Securifi Water Sensor"

	}

	// simulator metadata
	simulator {
		status "active": "zone report :: type: 19 value: 0021"
		status "inactive": "zone report :: type: 19 value: 0020"
	}

	// UI tile definitions
	tiles {
    	tiles(scale: 2) {
			multiAttributeTile(name:"moisture", type: "generic", width: 6, height: 4){
				tileAttribute ("device.moisture", key: "PRIMARY_CONTROL") {
					attributeState "dry", label: "Dry", icon:"st.alarm.water.dry", backgroundColor:"#ffffff"
					attributeState "wet", label: "Wet", icon:"st.alarm.water.wet", backgroundColor:"#53a7c0"
				}
			}
        }
		main (["moisture"])
		details(["moisture"])
	}
}

def configure() {
	log.debug("** PIR02 ** configure called for device with network ID ${device.deviceNetworkId}")

	String zigbeeId = swapEndianHex(device.hub.zigbeeId)
	log.debug "Configuring Reporting, IAS CIE, and Bindings."
	def configCmds = [
    	"zcl global write 0x500 0x10 0xf0 {${zigbeeId}}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 1", "delay 1500",

        "zcl global send-me-a-report 1 0x20 0x20 0x3600 0x3600 {01}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1500",

		"zdo bind 0x${device.deviceNetworkId} 1 1 0x001 {${device.zigbeeId}} {}", "delay 1500",

        "raw 0x500 {01 23 00 00 00}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1500",
	]
    return configCmds // send refresh cmds as part of config
}
def enrollResponse() {
	log.debug "Sending enroll response"
    [

	"raw 0x500 {01 23 00 00 00}", "delay 200",
    "send 0x${device.deviceNetworkId} 1 1"

    ]
}

// Parse incoming device messages to generate events
def parse(String description) {
	log.debug("** PIR02 parse received ** ${description}")
    def result = []
	Map map = [:]

    if (description?.startsWith('zone status')) {
	    map = parseIasMessage(description)
    }

	log.debug "Parse returned $map"
    map.each { k, v ->
    	log.debug("sending event ${v}")
        sendEvent(v)
    }

    if (description?.startsWith('enroll request')) {
    	List cmds = enrollResponse()
        log.debug "enroll response: ${cmds}"
        result = cmds?.collect { new physicalgraph.device.HubAction(it) }
    }
    return result
}

private Map parseIasMessage(String description) {
    List parsedMsg = description.split(' ')
    String msgCode = parsedMsg[2]

    Map resultMap = [:]
    switch(msgCode) {
        case '0x0020': // Dry
            log.debug 'Detected Dry'
            resultMap["moisture"] = [name: "moisture", value: "dry"]
            break

        case '0x0021': // Wet
            log.debug 'Detected Moisture'
            resultMap["moisture"] = [name: "moisture", value: "wet"]
            break
    }
    return resultMap
}

private hex(value) {
	new BigInteger(Math.round(value).toString()).toString(16)
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
    int i = 0;
    int j = array.length - 1;
    byte tmp;
    while (j > i) {
        tmp = array[j];
        array[j] = array[i];
        array[i] = tmp;
        j--;
        i++;
    }
    return array
}
