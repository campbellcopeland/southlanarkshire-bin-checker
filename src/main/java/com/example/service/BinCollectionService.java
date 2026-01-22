package com.example.service;

import com.example.ai.BinCollectionParser;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class BinCollectionService {
    private final BinCollectionParser parser;

    public BinCollectionService() {
        this.parser = new BinCollectionParser();
    }

    public Map<String, Object> getBinCollectionInfo(String url) {
        return parser.parseBinCollectionInfo(url);
    }

    public BinCollectionData formatBinData(Map<String, Object> rawData) {
        BinCollectionData data = new BinCollectionData();
        
        if ((Boolean) rawData.getOrDefault("success", false)) {
            data.setSuccess(true);
            data.setCollectionDays((java.util.List<?>) rawData.getOrDefault("collectionDays", java.util.List.of()));
            data.setBinColors((java.util.List<?>) rawData.getOrDefault("binColors", java.util.List.of()));
            data.setBinTypes((java.util.List<?>) rawData.getOrDefault("binTypes", java.util.List.of()));
            data.setBinSchedules((java.util.List<?>) rawData.getOrDefault("binSchedules", java.util.List.of()));
            
            // Filter out empty/null entries from thisWeekCollections
            java.util.List<?> rawThisWeek = (java.util.List<?>) rawData.getOrDefault("thisWeekCollections", java.util.List.of());
            java.util.List<Map<String, String>> filteredThisWeek = new java.util.ArrayList<>();
            for (Object item : rawThisWeek) {
                if (item instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> binMap = (Map<String, String>) item;
                    // Only include if it has a description (the bin type)
                    if (binMap.get("description") != null && !binMap.get("description").trim().isEmpty()) {
                        filteredThisWeek.add(binMap);
                    }
                }
            }
            data.setThisWeekCollections(filteredThisWeek);
            
            data.setPageTitle((String) rawData.getOrDefault("pageTitle", "Bin Collection Info"));
            @SuppressWarnings("unchecked")
            Map<String, String> location = (Map<String, String>) rawData.getOrDefault("location", new java.util.HashMap<>());
            data.setLocation(location);
        } else {
            data.setSuccess(false);
            data.setError((String) rawData.getOrDefault("error", "Unknown error"));
        }
        
        return data;
    }

    public static class BinCollectionData {
        private boolean success;
        private String error;
        private String pageTitle;
        private java.util.List<?> collectionDays;
        private java.util.List<?> binColors;
        private java.util.List<?> binTypes;
        private java.util.List<?> binSchedules;
        private java.util.List<?> thisWeekCollections;
        private Map<String, String> location;

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public String getPageTitle() { return pageTitle; }
        public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }

        public java.util.List<?> getCollectionDays() { return collectionDays; }
        public void setCollectionDays(java.util.List<?> collectionDays) { this.collectionDays = collectionDays; }

        public java.util.List<?> getBinColors() { return binColors; }
        public void setBinColors(java.util.List<?> binColors) { this.binColors = binColors; }

        public java.util.List<?> getBinTypes() { return binTypes; }
        public void setBinTypes(java.util.List<?> binTypes) { this.binTypes = binTypes; }

        public java.util.List<?> getBinSchedules() { return binSchedules; }
        public void setBinSchedules(java.util.List<?> binSchedules) { this.binSchedules = binSchedules; }

        public java.util.List<?> getThisWeekCollections() { return thisWeekCollections; }
        public void setThisWeekCollections(java.util.List<?> thisWeekCollections) { this.thisWeekCollections = thisWeekCollections; }

        public Map<String, String> getLocation() { return location; }
        public void setLocation(Map<String, String> location) { this.location = location; }
    }
}
