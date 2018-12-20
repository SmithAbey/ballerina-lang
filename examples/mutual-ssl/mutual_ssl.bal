import ballerina/http;
import ballerina/log;

// Create a new service endpoint to accept new connections
//that are secured via mutual SSL.
http:ServiceEndpointConfiguration helloWorldEPConfig = {

    secureSocket: {
        keyStore: {
            path: "${ballerina.home}/bre/security/ballerinaKeystore.p12",
            password: "ballerina"
        },
        trustStore: {
            path: "${ballerina.home}/bre/security/ballerinaTruststore.p12",
            password: "ballerina"
        },
        // Configure prefered SSL protocol and versions to enable.
        protocol: {
            name: "TLS",
            versions: ["TLSv1.2", "TLSv1.1"]
        },

        // Configure prefered ciphers.
        ciphers: ["TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"],

        // Enable mutual SSL.
        sslVerifyClient: "require"

    }
};

// Create a listener endpoint.
listener http:Listener helloWorldEP = new(9095, config = helloWorldEPConfig);

@http:ServiceConfig {
    basePath: "/hello"
}

// Bind the service to the listener endpoint that you declared above.
service helloWorld on helloWorldEP {
    @http:ResourceConfig {
        methods: ["GET"],
        path: "/"
    }

    resource function sayHello(http:Caller caller, http:Request req) {
        // Send response to caller.
        var result = caller->respond("Successful");

        if (result is error) {
            log:printError("Error in responding", err = result);
        }
    }
}

// Create a new client endpoint to connect to the service endpoint you created
// above via mutual SSL. The Ballerina client can be used to connect to the
// created HTTPS listener. Provide the `keyStoreFile`, `keyStorePassword`,
// `trustStoreFile` and `trustStorePassword` in the client.
http:ClientEndpointConfig clientEPConfig = {
    secureSocket: {
        keyStore: {
            path: "${ballerina.home}/bre/security/ballerinaKeystore.p12",
            password: "ballerina"
        },
        trustStore: {
            path: "${ballerina.home}/bre/security/ballerinaTruststore.p12",
            password: "ballerina"
        },
        protocol: {
            name: "TLS"
        },
        ciphers: ["TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"]
    }
};

public function main() {

    // Create an HTTP client to interact with the created listener endpoint.
    http:Client clientEP = new("https://localhost:9095",
                                config = clientEPConfig);

    // Send a get request to the server.
    var resp = clientEP->get("/hello");

    if (resp is http:Response) {
        // If the request is successful, retrieve the text payload from the
        // response.
        var payload = resp.getTextPayload();

        if (payload is string) {
            // Log the retrieved text paylod.
            log:printInfo(payload);

        } else {
            // If an error occurs while retrieving the text payload, log
            // the error.
            log:printError(<string>payload.detail().message);

        }
    } else {
        // If an error occurs when getting the response, log the error.
        log:printError(<string>resp.detail().message);

    }
}
