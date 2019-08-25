import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONObject;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;

public class InvestigateAwsIotADC {
	// Constant values of the Amazon Root CA.
	static String ROOT_CA_FILE_PATH = "AWSRootCaStore.bin";
	static String ROOT_CA_FILE_PASSWORD = "password_amazon_root_ca";

	// Constant values of a client certificate.
//	static String CLIENT_CERT_KEY_STORE_FILE_PATH = "client_cert_key_store.bin";
//	static String CLIENT_CERT_KEY_STORE_PASSWORD = "password_client_cert_key_store";
	static String CLIENT_CERT_KEY_STORE_PKCS12_FILE_PATH = "ClientCertKey.p12";
	static String CLIENT_CERT_KEY_STORE_PKCS12_PASSWORD = "password_client_cert_key_store";

	// Replace the domain name with yours.
	static String AWS_IOT_ROLE_ALIAS_ENDPOINT = "https://****YourEndpointDomain****/role-aliases/****YourRoleAlias****/credentials";
	static String AWS_IOT_THING_NAME = "****YourThingName***";
	static String AWS_IOT_ADC_HEADER_NAME = "x-amzn-iot-thingname";


	// Replace the AWS resources configurations with yours.
	static Regions AWS_REGION = Regions.AP_NORTHEAST_1;
	static String AWS_S3_BUCKET_NAME = "****YourBucketName****";
	static String AWS_S3_FILE_NAME = "****YourFile****";

	public static void main(String[] args) {
		System.out.println("Started.");

		CloseableHttpClient httpclient = createHttpClient();
		String response_body = invokeHttpRequest(httpclient);
		BasicSessionCredentials temporary_credentials = parseCredentialsFromHttpResponse(response_body);

		String s3object_content = getS3Object(temporary_credentials);
		System.out.println("The content of the S3 file: " + s3object_content);

		System.out.println("Finished.");
	}


	public static CloseableHttpClient createHttpClient() {
		// The "custom()" method returns "SSLContextBuilder".
		// Its parameters are a little complicated.
		//
		// https://hc.apache.org/httpcomponents-core-ga/httpcore/apidocs/org/apache/http/ssl/SSLContexts.html#custom()
		// https://hc.apache.org/httpcomponents-core-ga/httpcore/apidocs/org/apache/http/ssl/SSLContextBuilder.html
		SSLContext sslcontext = null;
		try {
			sslcontext = SSLContexts.custom()//
//				.loadKeyMaterial(new File(CLIENT_CERT_KEY_STORE_FILE_PATH), CLIENT_CERT_KEY_STORE_FILE_PASSWORD.toCharArray(), CLIENT_CERT_KEY_STORE_FILE_PASSWORD.toCharArray())
				.loadKeyMaterial(new File(CLIENT_CERT_KEY_STORE_PKCS12_FILE_PATH), CLIENT_CERT_KEY_STORE_PKCS12_PASSWORD.toCharArray(), CLIENT_CERT_KEY_STORE_PKCS12_PASSWORD.toCharArray())
				.loadTrustMaterial(new File(ROOT_CA_FILE_PATH), ROOT_CA_FILE_PASSWORD.toCharArray())
				.build();

		} catch (Exception e) {
			System.out.println("An Exception has been thrown while creating an SSLContext.");
			e.printStackTrace();
		}


		// https://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/conn/ssl/SSLConnectionSocketFactory.html
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext);

		// https://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/client/HttpClients.html
		return HttpClients.custom().setSSLSocketFactory(sslsf).build();

	}

	public static String invokeHttpRequest(CloseableHttpClient httpclient) {

		// Add a header specify an AWS IoT thing name described in the AWS IOT document below.
		// https://docs.aws.amazon.com/iot/latest/developerguide/authorizing-direct-aws.html

		HttpUriRequest request = RequestBuilder.get() //
				.setUri(AWS_IOT_ROLE_ALIAS_ENDPOINT)
				.setHeader(AWS_IOT_ADC_HEADER_NAME, AWS_IOT_THING_NAME)
				.build();


		System.out.println("Http request: "+ request.getRequestLine());

		try {
			CloseableHttpResponse response = httpclient.execute(request);

			// Prints an HTTP Status Code, HTTP Headers and an HTTP Response Body.
			System.out.println("Http Status Code: " + response.getStatusLine().getStatusCode());
			List<Header> headers = Arrays.asList(response.getAllHeaders());
			headers.forEach((header) -> {System.out.println("Http Response Header: " + header);});
			String response_body = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
			System.out.println("Http Response Body: " + response_body);

			response.close();

			return (response_body);

		} catch (IOException e) {
			System.out.println("An Exception has been thrown while invoking an HTTP request.");
			e.printStackTrace();
		}

		return null;
	}

	public static BasicSessionCredentials parseCredentialsFromHttpResponse(String http_response) {
		JSONObject response_json = new JSONObject(http_response);

		String access_key_id = response_json.getJSONObject("credentials").getString("accessKeyId");
		String secret_access_key = response_json.getJSONObject("credentials").getString("secretAccessKey");
		String session_token = response_json.getJSONObject("credentials").getString("sessionToken");

		return new BasicSessionCredentials(access_key_id, secret_access_key, session_token);
	}

	public static String getS3Object(BasicSessionCredentials temporary_credentials) {
		// See also the following document.
		// https://docs.aws.amazon.com/AmazonS3/latest/dev/AuthUsingTempSessionTokenJava.html
		AmazonS3 s3Client = AmazonS3ClientBuilder.standard() //
				.withCredentials(new AWSStaticCredentialsProvider(temporary_credentials))
				.withRegion(AWS_REGION)
				.build();

		S3Object s3object = s3Client.getObject(AWS_S3_BUCKET_NAME, AWS_S3_FILE_NAME);


		try {
			String s3object_content = IOUtils.toString(s3object.getObjectContent(), "UTF-8");
			return s3object_content;
		} catch (IOException e) {
			System.out.println("An Exception has been thrown while retreiving an S3 object.");
			e.printStackTrace();
		}

		return null;
	}

}
