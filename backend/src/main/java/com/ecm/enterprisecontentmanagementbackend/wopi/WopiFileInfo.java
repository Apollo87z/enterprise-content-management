package com.ecm.enterprisecontentmanagementbackend.wopi;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WopiFileInfo(
    @JsonProperty("BaseFileName") String baseFileName,
    @JsonProperty("Size") long size,
    @JsonProperty("Version") String version,
    @JsonProperty("OwnerId") String ownerId,
    @JsonProperty("UserId") String userId,
    @JsonProperty("UserFriendlyName") String userFriendlyName,
    @JsonProperty("UserCanWrite") boolean userCanWrite,
    @JsonProperty("SupportsUpdate") boolean supportsUpdate,
    @JsonProperty("SupportsLocks") boolean supportsLocks
) {}
