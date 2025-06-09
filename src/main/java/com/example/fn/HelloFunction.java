package com.example.fn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.InputEvent;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Base64;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.RuntimeContext;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import oracle.jdbc.OracleConnection;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.StringPrivateKeySupplier;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.ChatDetails;
import com.oracle.bmc.generativeaiinference.model.CohereChatRequest;
import com.oracle.bmc.generativeaiinference.model.CohereMessage;
import com.oracle.bmc.generativeaiinference.model.DedicatedServingMode;
import com.oracle.bmc.generativeaiinference.requests.ChatRequest;
import com.oracle.bmc.generativeaiinference.responses.ChatResponse;
import com.oracle.bmc.generativeaiinference.model.BaseChatRequest.ApiFormat;
import com.oracle.bmc.generativeaiinference.model.ChatChoice;
import com.oracle.bmc.generativeaiinference.model.ChatContent;
import com.oracle.bmc.generativeaiinference.model.ChatContent.Type;
import com.oracle.bmc.generativeaiinference.model.ChatResult;
import com.oracle.bmc.generativeaiinference.model.Message;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import com.oracle.bmc.generativeaiinference.model.ServingMode;
import com.oracle.bmc.generativeaiinference.model.TextContent;
import com.oracle.bmc.retrier.RetryConfiguration;
import com.oracle.bmc.database.requests.GenerateAutonomousDatabaseWalletRequest;
import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.database.model.GenerateAutonomousDatabaseWalletDetails;
import com.oracle.bmc.database.responses.GenerateAutonomousDatabaseWalletResponse;
import java.util.Random;
import java.util.zip.*;

public class HelloFunction {

    // JDBC CONNECTION POOL
    private PoolDataSource pds;

    // JDBC CONNECTION DETAILS
    private static String DB_USER;
    private static String DB_PASSWORD;
    private static String DB_URL;
    private static String DB_WALLET_OCID;
    private static String DB_WALLET_PASSWORD;

    // For SDK
    private static GenerativeAiInferenceClient generativeAiInferenceClient;
    private static DatabaseClient databaseClient;

    // GENAI OCI DETAILS
    private static Region REGION           = Region.EU_FRANKFURT_1;
    private static String COMPARTMENT_OCID = "";
    private static String GENAI_OCID       = "";
    private static String GENAI_ENDPOINT   = "";

    // APP CONFIGS
    private static String PAGE_URL          = "";
    private static String SIGNUP_URL        = "";
    private static String SIGNUP_PAGE_URL   = "";
    private static String CAROUSEL_PAGE_URL = "";
    private static String AUTH_URL          = "";
    private static String APP_URL           = "";
    private static String WELCOME_URL       = "";
    private static String IDCS_URL          = "";
    private static String PROFILE_ID        = "";

    // For testing native image locally
    public static void main(String[] args) {
        System.out.println("Main running ... testing DB connection ... ");
        try {
            try {
                ConfigFileAuthenticationDetailsProvider configFileAuthenticationDetailsProvider =
                        new ConfigFileAuthenticationDetailsProvider("~/.oci/config", "DEFAULT");
                databaseClient =
                        DatabaseClient.builder()
                                .region(REGION)
                                .endpoint(GENAI_ENDPOINT)
                                .build(configFileAuthenticationDetailsProvider);
            } catch (Exception ee) {
                System.out.println(ee.getMessage());
            }
            DB_USER = System.getenv().getOrDefault("DB_USER", "");
            DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");
            DB_URL = "jdbc:oracle:thin:@" + System.getenv().getOrDefault("DB_URL", "");
            DB_WALLET_OCID = System.getenv().getOrDefault("DB_WALLET_OCID", "");
            DB_WALLET_PASSWORD = System.getenv().getOrDefault("DB_WALLET_PASSWORD", "");
            Properties props = new Properties();
            props.put(OracleConnection.CONNECTION_PROPERTY_FAN_ENABLED, "false");
            PoolDataSource _pds = PoolDataSourceFactory.getPoolDataSource();
            _pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            if(DB_WALLET_OCID.length() > 0) {
                _pds.setURL(DB_URL + "?TNS_ADMIN=/tmp");
                System.out.println("Using mTLS with Wallet:" + DB_URL + "?TNS_ADMIN=/tmp");
                // Download wallet using SDK
                GenerateAutonomousDatabaseWalletDetails walletDetails = GenerateAutonomousDatabaseWalletDetails.builder()
                        .generateType(GenerateAutonomousDatabaseWalletDetails.GenerateType.Single)
                        .password(DB_WALLET_PASSWORD)
                        .isRegional(true)
                        .build();

                GenerateAutonomousDatabaseWalletRequest request = GenerateAutonomousDatabaseWalletRequest.builder()
                        .autonomousDatabaseId(DB_WALLET_OCID)
                        .generateAutonomousDatabaseWalletDetails(walletDetails)
                        .build();
                GenerateAutonomousDatabaseWalletResponse response = databaseClient.generateAutonomousDatabaseWallet(request);
                InputStream inputStream = response.getInputStream();
                File outputDir = new File("/tmp/");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                try (ZipInputStream zis = new ZipInputStream(inputStream)) {
                    ZipEntry entry;
                    byte[] buffer = new byte[4096];
                    while ((entry = zis.getNextEntry()) != null) {
                        System.out.println("DB WALLET file: " + entry.getName());
                        File outFile = new File(outputDir, entry.getName());
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        zis.closeEntry();
                    }
                }
            } else {
                System.out.println("Using TLS without Wallet");
                _pds.setURL(DB_URL);
            }
            _pds.setUser(DB_USER);
            _pds.setPassword(DB_PASSWORD);
            _pds.setConnectionPoolName("JDBC_UCP_POOL");
            _pds.setConnectionProperties(props);
            OracleConnection connection = (OracleConnection) _pds.getConnection();
            PreparedStatement userQuery = connection.prepareStatement("SELECT SYSDATE");
            ResultSet rs = userQuery.executeQuery();
            if(rs.next()) {
                System.out.println(rs.getString("SYSDATE"));
            }
            connection.close();
        } catch (Exception e)
        {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    @FnConfiguration
    public void setUp(RuntimeContext ctx) throws Exception {
        //OCI config
        COMPARTMENT_OCID = ctx.getConfigurationByKey("COMPARTMENT_ID").orElse(System.getenv().getOrDefault("COMPARTMENT_OCID", ""));
        GENAI_OCID = ctx.getConfigurationByKey("GENAI_OCID").orElse(System.getenv().getOrDefault("GENAI_OCID", ""));
        GENAI_ENDPOINT = ctx.getConfigurationByKey("GENAI_ENDPOINT").orElse(System.getenv().getOrDefault("GENAI_ENDPOINT", ""));

        // JDBC CONNECTION DETAILS - try reading from Application and Function level config
        DB_USER = ctx.getConfigurationByKey("DB_USER").orElse(System.getenv().getOrDefault("DB_USER", ""));
        DB_PASSWORD = ctx.getConfigurationByKey("DB_PASSWORD").orElse(System.getenv().getOrDefault("DB_PASSWORD", ""));
        DB_URL = "jdbc:oracle:thin:@" + ctx.getConfigurationByKey("DB_URL").orElse(System.getenv().getOrDefault("DB_URL", ""));
        DB_WALLET_OCID = ctx.getConfigurationByKey("DB_WALLET_OCID").orElse(System.getenv().getOrDefault("DB_WALLET_OCID", ""));
        DB_WALLET_PASSWORD = ctx.getConfigurationByKey("DB_WALLET_PASSWORD").orElse(System.getenv().getOrDefault("DB_WALLET_PASSWORD", ""));

        PAGE_URL = ctx.getConfigurationByKey("PAGE_URL").orElse(System.getenv().getOrDefault("PAGE_URL", ""));
        SIGNUP_URL = ctx.getConfigurationByKey("SIGNUP_URL").orElse(System.getenv().getOrDefault("SIGNUP_URL", ""));
        SIGNUP_PAGE_URL = ctx.getConfigurationByKey("SIGNUP_PAGE_URL").orElse(System.getenv().getOrDefault("SIGNUP_PAGE_URL", ""));
        CAROUSEL_PAGE_URL = ctx.getConfigurationByKey("CAROUSEL_PAGE_URL").orElse(System.getenv().getOrDefault("CAROUSEL_PAGE_URL", ""));
        AUTH_URL = ctx.getConfigurationByKey("AUTH_URL").orElse(System.getenv().getOrDefault("AUTH_URL", ""));
        APP_URL = ctx.getConfigurationByKey("APP_URL").orElse(System.getenv().getOrDefault("APP_URL", ""));
        WELCOME_URL = ctx.getConfigurationByKey("WELCOME_URL").orElse(System.getenv().getOrDefault("WELCOME_URL", ""));
        IDCS_URL = ctx.getConfigurationByKey("IDCS_URL").orElse(System.getenv().getOrDefault("IDCS_URL", ""));
        PROFILE_ID = ctx.getConfigurationByKey("PROFILE_ID").orElse(System.getenv().getOrDefault("PROFILE_ID", ""));

        try {
            ResourcePrincipalAuthenticationDetailsProvider resourcePrincipalAuthenticationDetailsProvider =
                    ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            generativeAiInferenceClient =
                    GenerativeAiInferenceClient.builder()
                            .region(REGION)
                            .endpoint(GENAI_ENDPOINT)
                            .build(resourcePrincipalAuthenticationDetailsProvider);
            databaseClient =
                    DatabaseClient.builder()
                            .region(REGION)
                            .endpoint(GENAI_ENDPOINT)
                            .build(resourcePrincipalAuthenticationDetailsProvider);

        } catch (Exception e) {
            try {
                ConfigFileAuthenticationDetailsProvider configFileAuthenticationDetailsProvider =
                        new ConfigFileAuthenticationDetailsProvider("/config", "DEFAULT");
                generativeAiInferenceClient =
                        GenerativeAiInferenceClient.builder()
                                .region(REGION)
                                .endpoint(GENAI_ENDPOINT)
                                .build(configFileAuthenticationDetailsProvider);
                databaseClient =
                        DatabaseClient.builder()
                                .region(REGION)
                                .endpoint(GENAI_ENDPOINT)
                                .build(configFileAuthenticationDetailsProvider);
            } catch (Exception ee) {
                System.out.println(ee.getMessage());
            }
        }

        try {
            Properties props = new Properties();
            props.put(OracleConnection.CONNECTION_PROPERTY_FAN_ENABLED, "false");
            pds = PoolDataSourceFactory.getPoolDataSource();
            pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            if(DB_WALLET_OCID.length() > 0) {
                pds.setURL(DB_URL + "?TNS_ADMIN=/tmp");
                System.out.println("Using mTLS with Wallet:" + DB_URL + "?TNS_ADMIN=/tmp");
                // Download wallet using SDK
                GenerateAutonomousDatabaseWalletDetails walletDetails = GenerateAutonomousDatabaseWalletDetails.builder()
                        .generateType(GenerateAutonomousDatabaseWalletDetails.GenerateType.Single)
                        .password(DB_WALLET_PASSWORD)
                        .isRegional(true)
                        .build();

                GenerateAutonomousDatabaseWalletRequest request = GenerateAutonomousDatabaseWalletRequest.builder()
                        .autonomousDatabaseId(DB_WALLET_OCID)
                        .generateAutonomousDatabaseWalletDetails(walletDetails)
                        .build();
                GenerateAutonomousDatabaseWalletResponse response = databaseClient.generateAutonomousDatabaseWallet(request);
                InputStream inputStream = response.getInputStream();
                File outputDir = new File("/tmp/");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                try (ZipInputStream zis = new ZipInputStream(inputStream)) {
                    ZipEntry entry;
                    byte[] buffer = new byte[4096];
                    while ((entry = zis.getNextEntry()) != null) {
                        System.out.println("DB WALLET file: " + entry.getName());
                        File outFile = new File(outputDir, entry.getName());
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        zis.closeEntry();
                    }
                }
            } else {
                System.out.println("Using TLS without Wallet");
                pds.setURL(DB_URL);
            }
            pds.setUser(DB_USER);
            pds.setPassword(DB_PASSWORD);
            pds.setConnectionPoolName("JDBC_UCP_POOL");
            pds.setConnectionProperties(props);
        } catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }

    public String handleRequest(final HTTPGatewayContext hctx, final InputEvent input) {

        String bearer = "";
        String ret = "";
        String alert = "";
        String action = hctx.getQueryParameters().get("action").orElse("");

        // Template parameters
        // Only for logged-in users with a valid bearer
        String SUB = "";
        String FIRST_NAME = "";
        String LAST_NAME = "";
        String PICTURE_URL = "";
        String TESTIMONIAL = "";
        String GENERATED_TESTIMONIAL = "";
        String SUPPORT_CASE_ID = "";
        String ID_TOKEN = "";

        System.out.println("==== FUNC ====");
        try {
            List<String> lines = Files.readAllLines(Paths.get("/func.yaml")).stream().limit(3).collect(Collectors.toList());
            lines.forEach(System.out::println);
        } catch (Exception e) {
            System.out.println("Error reading func.yaml: " + e.getMessage());
        }
        System.out.println("==============");

        System.out.println("======= CONFIG ======");
        //hctx.getHeaders().getAll().forEach((key, value) -> System.out.println(key + ": " + value));
        //input.getHeaders().getAll().forEach((key, value) -> System.out.println(key + ": " + value));
        hctx.getQueryParameters().getAll().forEach((key, value) -> System.out.println(key + ": " + value));
        //String[] configTokens = authConfig.split(",");
        //List<String> tokenizedConfig = Arrays.stream(configTokens).map(String::trim).collect(Collectors.toList());
        System.out.println("=====================");

        //Redirect to signup
        if(action.equals("signup")) {
            String signUpUrl = "https://" + IDCS_URL + ".identity.oraclecloud.com:443/ui/v1/signup?profileid=" + PROFILE_ID;
            hctx.setResponseHeader("Location", signUpUrl);
            hctx.setStatusCode(302);
            ret = ret + " Redirect to signout " + signUpUrl;
            return ret;
        }

        // Get Sub from bearer cookie
        // Get id_token from cookie for logout
        String cookies = hctx.getHeaders().get("Cookie").orElse(null);
        if(cookies != null) {
            String[] cookieTokens = cookies.split(";");
            List<String> tokenizedCookies = Arrays.stream(cookieTokens).map(String::trim).collect(Collectors.toList());
            for (String cookie : tokenizedCookies) {
                System.out.println(cookie);
                if (cookie.indexOf("bearer=") > -1) {
                    bearer = cookie.substring(cookie.indexOf("bearer=") + 7, cookie.length());
                    if(bearer.length() > 0)
                    {
                        SUB = getSubFromJwt(bearer);
                        System.out.println("Sub from BEARER COOKIE: " + SUB);
                        JwtData jwt = getJwt(bearer);
                        System.out.println("Display-name from BEARER COOKIE: " + jwt.user_displayname);
                        if(jwt.user_displayname.length() > 0)
                        {
                            String[] names = jwt.user_displayname.split(" ");
                            FIRST_NAME = names[0] != null ? names[0] : "";
                            LAST_NAME = names[1] != null ? names[1] : "";
                        }
                    }
                }
                if (cookie.indexOf("id_token=") > -1) {
                    ID_TOKEN = cookie.substring(cookie.indexOf("id_token=") + 9, cookie.length());
                    System.out.println("id_token from COOKIE: " + ID_TOKEN);
                }
            }
        }

        // Show homepage only when Sub not set for Anynomous users
        // with some basic template handling loading the content from Object Storage
        if(SUB == null || SUB.length() == 0)
        {
            String items = getCarouselItems(CAROUSEL_PAGE_URL);
            try {
                URL urlObject = new URL(SIGNUP_PAGE_URL);
                URLConnection urlConnection = urlObject.openConnection();
                InputStream inputStream = urlConnection.getInputStream();
                ret = ret + readFromInputStream(inputStream);
                ret = ret.replace("CAROUSEL_ITEMS", items);
                ret = ret.replace("SIGNUP_URL", SIGNUP_URL);
                ret = ret.replace("SIGNIN_URL", AUTH_URL);
                hctx.setResponseHeader("Content-type", "text/html");
            } catch (Exception e)
            {
                System.out.println("Error:" + e.getMessage());
                ret = e.getMessage();
            }
            return ret;
        }

        // Read ID_TOKEN from initial request for signout later
        // In case of GET generate a random profile pic
        if(hctx.getMethod().equalsIgnoreCase("GET")) {
            //ID_TOKEN = hctx.getQueryParameters().get("id_token").orElse("");
            if(hctx.getQueryParameters().get("id_token").orElse(null) != null)
            {
                ID_TOKEN = hctx.getQueryParameters().get("id_token").orElse("");
                String cookie = "id_token=" + ID_TOKEN; // + "; HttpOnly";
                System.out.println(cookie);
                hctx.setResponseHeader("Set-Cookie",cookie);
                hctx.setResponseHeader("Location", APP_URL);
                hctx.setStatusCode(302);
            }
            if(PICTURE_URL.length() == 0) {
                Random rand = new Random();
                int randomNumber = rand.nextInt(20) + 1;
                int randomGender = rand.nextInt(10) + 1;
                PICTURE_URL = "https://randomuser.me/api/portraits/" + (randomGender > 5 ? "men" : "women") + "/" + +randomNumber + ".jpg";
            }
        }

        // In case of POST get input variables for the request
        if(hctx.getMethod().equalsIgnoreCase("POST")) {
            String body = input.consumeBody((InputStream is) -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    return reader.lines().collect(Collectors.joining());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            if(body.length() > 0) {
                String[] bodyTokens = body.split("&");
                List<String> tokenizedBody = Arrays.stream(bodyTokens).map(String::trim).collect(Collectors.toList());
                for (String token : tokenizedBody) {
                    if (token.indexOf("first_name=") > -1) {
                        FIRST_NAME = token.substring(11, token.length());
                        FIRST_NAME = URLDecoder.decode(FIRST_NAME);
                    }
                    if (token.indexOf("last_name=") > -1) {
                        LAST_NAME = token.substring(10, token.length());
                        LAST_NAME = URLDecoder.decode(LAST_NAME);
                    }
                    if (token.indexOf("picture_url=") > -1) {
                        PICTURE_URL = token.substring(12, token.length());
                        PICTURE_URL = URLDecoder.decode(PICTURE_URL);
                    }
                    if (token.indexOf("user_testimonial=") > -1) {
                        TESTIMONIAL = token.substring(17, token.length());
                        TESTIMONIAL = URLDecoder.decode(TESTIMONIAL);
                    }
                    if (token.indexOf("generated_testimonial=") > -1) {
                        GENERATED_TESTIMONIAL = token.substring(22, token.length());
                        GENERATED_TESTIMONIAL = URLDecoder.decode(GENERATED_TESTIMONIAL);
                    }
                    if (token.indexOf("support_case_id=") > -1) {
                        SUPPORT_CASE_ID = token.substring(16, token.length());
                        System.out.println("SUPPORT CASE ID:" + SUPPORT_CASE_ID);
                    }
                    if (token.indexOf("id_token=") > -1) {
                        ID_TOKEN = token.substring(9, token.length());
                    }
                    if (token.indexOf("action=") > -1) {
                        action = token.substring(7, token.length());
                    }
                }
            }
        }

        // Get the support request depending if it is GET or POST
        // For GET expects the supportCaseId to be set if requested from JS
        // For POST expects the SUPPORT_CASE_ID to be set
        SUPPORT_CASE_ID = hctx.getQueryParameters().get("supportCaseId").orElse("");
        System.out.println("SUPPORT CASE ID:" + SUPPORT_CASE_ID);
        String support_case_description = "";
        try {
            String sql = "";
            OracleConnection connection = (OracleConnection) pds.getConnection();
            if(SUPPORT_CASE_ID == "")
            {
                sql = "SELECT case_id, description FROM ( SELECT * FROM support_case ORDER BY DBMS_RANDOM.VALUE) WHERE ROWNUM = 1";
            } else {
                sql = "SELECT case_id, description FROM support_case WHERE case_id = " + SUPPORT_CASE_ID;
            }
            PreparedStatement userQuery = connection.prepareStatement(sql);
            ResultSet rs = userQuery.executeQuery();
            if(rs.next()) {
                SUPPORT_CASE_ID = "" + rs.getLong("case_id");
                support_case_description = rs.getString("description");
                support_case_description = support_case_description.replace("Product:", "<b>Product</b>:");
                support_case_description = support_case_description.replace("Date of Purchase:", "<br><b>Date of Purchase</b>:");
                support_case_description = support_case_description.replace("Case Opened:", "<br><b>Case Opened</b>:");
                support_case_description = support_case_description.replace("Issue Description:", "<br><b>Issue Description</b>:");
                support_case_description = support_case_description.replace("Steps Taken by Customer:", "<br><b>Steps Taken by Customer</b>:");
                support_case_description = support_case_description.replace("Additional Notes:", "<br><b>Additional Notes</b>:");
                support_case_description = support_case_description.replace("Assigned Support Agent:", "<br><b>Assigned Support Agent</b>:");
                support_case_description = support_case_description.replace("Status:", "<br><b>Status</b>:");
                support_case_description = support_case_description.replace("Resolution Details:", "<br><b>Resolution Details</b>:");
            }
            connection.close();
        } catch (Exception e)
        {
            System.out.println(e.getMessage());
            ret = e.getMessage();
        }

        // GET ACTIONS for JS Fetch API
        if(hctx.getMethod().equalsIgnoreCase("GET") && action.equals("generate"))
        {
            String testimonialKeyWords = hctx.getQueryParameters().get("testimonialKeyWords").orElse("");
            try {
                String product = support_case_description.substring(support_case_description.indexOf("<b>Product</b>:") + 15, support_case_description.indexOf("<b>Date of Purchase</b>:"));
                String resolution = support_case_description.substring(support_case_description.indexOf("<b>Resolution Details</b>:") + 26, support_case_description.length());
                System.out.println("JS REQUEST PRODUCT:" + product);
                System.out.println("JS REQUEST RESOLUTION:" + resolution);
                System.out.println("JS REQUEST KEYWORDS:" + URLDecoder.decode(testimonialKeyWords));
                String questionToAI = "Generate a customer support testimonial in 40 words for the product " + product + " support case resolution: " + resolution + " and feedback : " + URLDecoder.decode(testimonialKeyWords);
                String testimonial = generateTestimonial(questionToAI);
                hctx.setResponseHeader("Content-type", "application/json");
                return "{\"testimonial\":\"" + testimonial + "\"}";
            } catch (Exception e) {
                hctx.setStatusCode(500);
                System.out.println(e.getMessage());
                return e.getMessage();
            }
        }

        // POST ACTIONS
        // - signout, generate, send
        // - only for logged-in users
        if(hctx.getMethod().equalsIgnoreCase("POST") && action.equals("signout"))
        {
            // Clear cookies and fwd to welcome page
            hctx.setResponseHeader("Set-Cookie","bearer="); // + "; HttpOnly");
            //String signoutUrl = WELCOME_URL;
            String signoutUrl = "https://" + IDCS_URL + ".identity.oraclecloud.com:443/oauth2/v1/userlogout?post_logout_redirect_uri=" + WELCOME_URL + "&id_token_hint=" + ID_TOKEN;
            hctx.setResponseHeader("Location", signoutUrl);
            hctx.setStatusCode(302);
            ret = ret + " Redirect to signout " + signoutUrl;
            return ret;
        } else if(action.equals("generate"))
        {
            if(TESTIMONIAL.length() > 0) {
                if(!TESTIMONIAL.equals(GENERATED_TESTIMONIAL)) {
                    String questionToAI = "Generate a customer support testimonial in 30 words for the support case " + support_case_description + " without any customer, customer support or company names and with feedback criteria : " + TESTIMONIAL;
                    //ret = ret + questionToAI;
                    try {
                        TESTIMONIAL = generateTestimonial(questionToAI);
                        GENERATED_TESTIMONIAL = TESTIMONIAL;
                        alert = "<div class=\"alert alert-primary\" role=\"alert\">\n" +
                                "  Testimonial was generated, now you can submit it or modify it yourself before submitting. To generate again, enter keywords and press generate." +
                                "</div>";
                    } catch (Exception e) {
                        ret = ret + e.getMessage();
                    }
                } else {
                    alert = "<div class=\"alert alert-warning\" role=\"alert\">\n" +
                            "  After testimonial generation you can submit it. If you are not happy with the generated text, enter new keywords and generate again." +
                            "</div>";
                }
            } else {
                alert = "<div class=\"alert alert-danger\" role=\"alert\">\n" +
                        "  Give some info and/or keywords to generate the testimonial, thanks!" +
                        "</div>";
            }
        } else if(action.equals("send"))
        {
            if(TESTIMONIAL.length() > 0 && FIRST_NAME.length() > 0 && LAST_NAME.length() > 0) {
                try {

                    OracleConnection connection = (OracleConnection) pds.getConnection();
                    String sql = "INSERT INTO testimonial (userid, username, description, userpicture_url) values  (?, ?, ?, ?)";
                    PreparedStatement pstmt = connection.prepareStatement(sql);
                    pstmt.setString(1, SUB);
                    pstmt.setString(2, FIRST_NAME + " " + LAST_NAME);
                    pstmt.setString(3, TESTIMONIAL);
                    pstmt.setString(4, PICTURE_URL);
                    pstmt.executeUpdate();
                    connection.close();
                    alert = "<div class=\"alert alert-success\" role=\"alert\">\n" +
                            "  Testimonial submitted succesfull, thank you! You can now sign out from this page." +
                            "</div>";
                    TESTIMONIAL = "";
                    GENERATED_TESTIMONIAL = "";
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    ret = e.getMessage();
                }
            } else {
                alert = "<div class=\"alert alert-danger\" role=\"alert\">\n" +
                        "  Fill in your name and testimonial before submitting, thanks!" +
                        "</div>";
            }
        }

        // Finally some basic template handling for page output loading the content from Object Storage
       try {
           URL urlObject = new URL(PAGE_URL);
           URLConnection urlConnection = urlObject.openConnection();
           InputStream inputStream = urlConnection.getInputStream();
           ret = ret + readFromInputStream(inputStream);
           ret = ret.replace("ALERT_TEXT", alert);
           ret = ret.replace("SR_TEXT", support_case_description);
           ret = ret.replace("USER_VALUE", SUB);
           ret = ret.replace("FIRST_NAME_VALUE", FIRST_NAME);
           ret = ret.replace("LAST_NAME_VALUE", LAST_NAME);
           ret = ret.replace("PICTURE_URL_VALUE", PICTURE_URL);
           ret = ret.replace("TESTIMONIAL_VALUE", TESTIMONIAL);
           ret = ret.replace("GENERATED_VALUE", GENERATED_TESTIMONIAL);
           ret = ret.replace("SUPPORT_CASE_ID_VALUE", SUPPORT_CASE_ID);
           ret = ret.replace("ID_TOKEN_VALUE", ID_TOKEN);
           ret = ret.replace("APP_URL", APP_URL);
           if(GENERATED_TESTIMONIAL.length() > 0)
           {
               ret = ret.replace("SUBMIT_DISABLED", "");
           } else {
               ret = ret.replace("SUBMIT_DISABLED", "disabled");
           }
           hctx.setResponseHeader("Content-type", "text/html");
       } catch (Exception e)
       {
           System.out.println("Error:" + e.getMessage());
           ret = e.getMessage();
       }
       return ret;
    }

    private String readFromInputStream(InputStream inputStream)
            throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        try (BufferedReader br
                     = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }
        return resultStringBuilder.toString();
    }

    private String generateTestimonial(String questionToAI) throws Exception{
        LocalDate date = LocalDate.now().minusYears(100);
        CohereChatRequest chatRequest = CohereChatRequest.builder()
                .message(questionToAI)
                .maxTokens(600)
                .temperature((double) 0)
                .frequencyPenalty((double) 1)
                .topP((double) 0.75)
                .topK((int) 0)
                .isStream(false)
                .build();

        ChatDetails chatDetails = ChatDetails.builder()
                .servingMode(OnDemandServingMode.builder().modelId(GENAI_OCID).build())
                .compartmentId(COMPARTMENT_OCID)
                .chatRequest(chatRequest)
                .build();

        ChatRequest request = ChatRequest.builder()
                .chatDetails(chatDetails)
                .build();

        ChatResponse chatResponse = generativeAiInferenceClient.chat(request);
        String answerAI = chatResponse.toString();
        answerAI = answerAI.substring(answerAI.indexOf("text=") + 5);
        if (answerAI.indexOf(", chatHistory=") > 0) {
            answerAI = answerAI.substring(0, answerAI.indexOf(", chatHistory="));
        }
        answerAI = answerAI.replaceAll("\"", "");
        return answerAI;
    }

    private String getCarouselItems(String url)
    {
        String items = "";
        try {
            URL urlObject = new URL(url);
            URLConnection urlConnection = urlObject.openConnection();
            InputStream inputStream = urlConnection.getInputStream();
            String oneItem = readFromInputStream(inputStream);
            OracleConnection connection = (OracleConnection) pds.getConnection();
            //String sql = "SELECT username, userpicture_url, description FROM (SELECT * FROM testimonial ORDER BY DBMS_RANDOM.VALUE) WHERE ROWNUM <= 3";
            String sql = "SELECT username, userpicture_url, description FROM (SELECT * FROM testimonial ORDER BY ID DESC) WHERE ROWNUM <= 3";
            PreparedStatement userQuery = connection.prepareStatement(sql);
            ResultSet rs = userQuery.executeQuery();
            int i = 0;
            while(rs.next()) {
                String username = rs.getString("username");
                String userpicture_url = rs.getString("userpicture_url");
                String description = rs.getString("description");
                String item = oneItem;
                item = item.replace("ACTIVE", (i == 0 ? "active" : ""));
                item = item.replace("NAME", username);
                item = item.replace("PROFILE_PIC", userpicture_url);
                item = item.replace("TESTIMONIAL", description);
                items = items + item;
                i++;
            }
            connection.close();
            return items;
        } catch (Exception e)
        {
            System.out.println("Error:" + e.getMessage());
            return e.getMessage();
        }
    }

    private String getSubFromJwt(String bearer) {
        String sub = null;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String[] chunks = bearer.split("\\.");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payload = new String(decoder.decode(chunks[1]));
            JwtData jwtData = objectMapper.readValue(payload, JwtData.class);
            sub = jwtData.sub;
        } catch (Exception e)
        {
            System.out.println("Sub cannot be read from bearer, error:" + e.getMessage());
        }
        return sub;
    }

    private JwtData getJwt(String bearer) {
        JwtData jwtData = null;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String[] chunks = bearer.split("\\.");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payload = new String(decoder.decode(chunks[1]));
            jwtData = objectMapper.readValue(payload, JwtData.class);
        } catch (Exception e)
        {
            System.out.println("Sub cannot be read from bearer, error:" + e.getMessage());
        }
        return jwtData;
    }
}