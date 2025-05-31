package com.example.fn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JwtData {
    public String user_tz;
    public String sub;
    public String user_locale;
    public String scope;
    public String user_ocid;
    public String user_displayname;
    public String client_name;
}
