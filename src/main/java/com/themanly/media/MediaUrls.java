package com.themanly.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class MediaUrls {
    private final String baseUrl;
    public MediaUrls(@Value("${app.media.base-url:/media}") String baseUrl) {this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;}
    public String urlFor(MediaAsset asset) {return baseUrl + "/" + asset.getStorageKey();}
}