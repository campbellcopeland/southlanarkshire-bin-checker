package com.example.ai;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BinCollectionParser {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int TIMEOUT = 10000;
    
    // Magic number constants
    private static final int MIN_TEXT_LENGTH = 20;
    private static final int MAX_TEXT_LENGTH = 500;
    private static final int CONTEXT_WINDOW = 100;
    
    // Bin type constants
    private static final String BLUE_BIN_DESC = "Blue bin - Paper and card";
    private static final String BURGUNDY_BIN_DESC = "Burgundy bin - Food and garden waste";
    private static final String LIGHT_GREY_BIN_DESC = "Light grey bin - Glass, cans and plastics";
    private static final String BLACK_BIN_DESC = "Black/Green bin - General waste";
    
    private static final String COLLECTION_THIS_WEEK = "Collection this week";
    
    // Section boundary markers
    private static final Set<String> SECTION_BOUNDARIES = Set.of(
        "fortnightly", "next week", "area"
    );

    public Map<String, Object> parseBinCollectionInfo(String url) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Fetch the web page
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT)
                    .get();

            // Extract bin collection information
            Map<String, Object> binInfo = extractBinInfo(doc);
            result.put("success", true);
            result.putAll(binInfo);
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Failed to fetch URL: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> extractBinInfo(Document doc) {
        Map<String, Object> binInfo = new HashMap<>();
        List<Map<String, String>> bins = new ArrayList<>();
        List<Map<String, String>> thisWeekBins = new ArrayList<>();

        String pageText = doc.body().text();
        String lowerPageText = pageText.toLowerCase(); // Cache lowercased version
        
        // Search for tables and divs that might contain bin info
        Elements tables = doc.select("table");
        Elements divs = doc.select("div[class*='info'], div[class*='content'], div[class*='schedule']");

        // Extract from tables
        for (Element table : tables) {
            extractFromTable(table, bins, thisWeekBins);
        }

        // Extract from divs with specific keywords
        for (Element div : divs) {
            String text = div.text();
            String lowerText = text.toLowerCase();
            if (lowerText.contains("collection") || 
                lowerText.contains("bin") ||
                lowerText.contains("waste")) {
                extractBinDetails(div, bins, thisWeekBins);
            }
        }

        // Search for "This week" specifically
        extractThisWeekInfo(lowerPageText, pageText, thisWeekBins);

        // Search for color keywords and collection day information
        List<String> collectionDays = extractCollectionDays(pageText, lowerPageText);
        List<String> colors = extractBinColors(pageText);
        List<String> binTypes = extractBinTypes(lowerPageText);
        Map<String, String> location = extractLocation(doc, pageText);

        binInfo.put("collectionDays", collectionDays);
        binInfo.put("binColors", colors);
        binInfo.put("binTypes", binTypes);
        binInfo.put("binSchedules", bins);
        binInfo.put("thisWeekCollections", thisWeekBins);
        binInfo.put("pageTitle", doc.title());
        binInfo.put("location", location);

        return binInfo;
    }

    private void extractFromTable(Element table, List<Map<String, String>> bins, List<Map<String, String>> thisWeekBins) {
        Elements rows = table.select("tr");
        for (Element row : rows) {
            Elements cells = row.select("td, th");
            if (cells.size() >= 2) {
                String key = cells.get(0).text().trim();
                String value = cells.get(1).text().trim();
                
                // Skip empty or header rows
                if (key.isEmpty() || value.isEmpty() || 
                    key.equalsIgnoreCase("Bin Type") || key.equalsIgnoreCase("Schedule") ||
                    key.equalsIgnoreCase("Area")) {
                    continue;
                }
                
                Map<String, String> schedule = new HashMap<>();
                schedule.put("description", key);
                schedule.put("schedule", value);
                bins.add(schedule);
                
                // Only add to thisWeek if it explicitly says "this week" (which it won't in these tables)
                // The table shows the schedule, not this week's collections
                // This week info comes from the separate section
            }
        }
    }

    private void extractBinDetails(Element element, List<Map<String, String>> bins, List<Map<String, String>> thisWeekBins) {
        String text = element.text();
        if (text.length() > MIN_TEXT_LENGTH && text.length() < MAX_TEXT_LENGTH) {
            Map<String, String> bin = new HashMap<>();
            bin.put("details", text);
            bins.add(bin);
            
            // Check if this is this week's information
            if (text.toLowerCase().contains("this week")) {
                thisWeekBins.add(bin);
            }
        }
    }

    private void extractThisWeekInfo(String lowerPageText, String pageText, List<Map<String, String>> thisWeekBins) {
        // Look for "This week's collection" section ONLY
        int thisWeekIndex = lowerPageText.indexOf("this week");
        
        if (thisWeekIndex == -1) {
            // No "This week's collection" section means no bins collected this week
            return;
        }
        
        // Find the exact boundaries of the "This week's collection" section
        String afterThisWeek = pageText.substring(thisWeekIndex);
        String lowerAfter = afterThisWeek.toLowerCase();
        
        int sectionEnd = afterThisWeek.length();
        
        // Find section boundaries (these typically mark the end of "this week")
        for (String boundary : SECTION_BOUNDARIES) {
            int boundaryIndex = lowerAfter.indexOf(boundary);
            if (boundaryIndex > 0 && boundaryIndex < sectionEnd) {
                sectionEnd = boundaryIndex;
            }
        }
        
        // Check for any month name (dynamic month detection)
        String currentMonth = LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase();
        String nextMonth = LocalDate.now().plusMonths(1).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase();
        
        int currentMonthIdx = lowerAfter.indexOf(currentMonth);
        int nextMonthIdx = lowerAfter.indexOf(nextMonth);
        
        if (currentMonthIdx > 0 && currentMonthIdx < sectionEnd) {
            sectionEnd = currentMonthIdx;
        }
        if (nextMonthIdx > 0 && nextMonthIdx < sectionEnd) {
            sectionEnd = nextMonthIdx;
        }
        
        // Cap at reasonable distance to avoid picking up unrelated content
        sectionEnd = Math.min(sectionEnd, MAX_TEXT_LENGTH);
        
        String lowerSection = lowerAfter.substring(0, sectionEnd);
        
        // Extract bins using helper method to reduce duplication
        addBinIfPresent(lowerSection, "blue bin", "blue - paper", BLUE_BIN_DESC, "blue", thisWeekBins, false);
        addBinIfPresent(lowerSection, "burgundy bin", "burgundy - food", BURGUNDY_BIN_DESC, "burgundy", thisWeekBins, false);
        addBinIfPresent(lowerSection, "light grey bin", "light grey - glass", LIGHT_GREY_BIN_DESC, "light", thisWeekBins, true);
        addBinIfPresent(lowerSection, "black bin", "black - general", BLACK_BIN_DESC, "black", thisWeekBins, true);
        
        // Also check for "black/green bin" variant
        if (lowerSection.contains("black/green bin") && 
            !lowerSection.contains("4 weekly") && !lowerSection.contains("fortnightly")) {
            addBinToList(BLACK_BIN_DESC, "black", thisWeekBins);
        }
    }
    
    private void addBinIfPresent(String lowerSection, String pattern1, String pattern2, 
                                  String description, String color, 
                                  List<Map<String, String>> thisWeekBins, 
                                  boolean checkFrequency) {
        if (lowerSection.contains(pattern1) || lowerSection.contains(pattern2)) {
            if (checkFrequency) {
                // Only add if not marked as fortnightly/4 weekly
                if (lowerSection.contains("4 weekly") || lowerSection.contains("fortnightly")) {
                    return;
                }
            }
            addBinToList(description, color, thisWeekBins);
        }
    }
    
    private void addBinToList(String description, String color, List<Map<String, String>> thisWeekBins) {
        Map<String, String> bin = new HashMap<>();
        bin.put("description", description);
        bin.put("schedule", COLLECTION_THIS_WEEK);
        bin.put("color", color);
        thisWeekBins.add(bin);
    }

    private List<String> extractCollectionDays(String text, String lowerText) {
        Set<String> daysSet = new HashSet<>();
        
        // Extract collection days from the schedule table/section, NOT from "This week's collection" 
        // (which just shows the calendar week date range)
        // Look for the pattern "BinType - Description    Day (Frequency)"
        
        String[] allDays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        
        // Look for collection schedule patterns like "Friday (Fortnightly)" or "Friday (4 Weekly)"
        for (String day : allDays) {
            String pattern = day + " (";  // Matches "Friday (" but not just "Friday" in a date
            if (text.contains(pattern)) {
                daysSet.add(day);
            }
        }
        
        // If no pattern found, try simpler approach: look for days in collection/schedule context
        if (daysSet.isEmpty()) {
            for (String day : allDays) {
                int dayIdx = lowerText.indexOf(day.toLowerCase());
                if (dayIdx != -1) {
                    // Check if it's in a collection context (near "fortnightly", "weekly", "bin", etc.)
                    int contextStart = Math.max(0, dayIdx - CONTEXT_WINDOW);
                    int contextEnd = Math.min(text.length(), dayIdx + CONTEXT_WINDOW);
                    String context = text.substring(contextStart, contextEnd).toLowerCase();
                    
                    if (context.contains("fortnightly") || context.contains("weekly") || 
                        context.contains("bin") || context.contains("waste")) {
                        daysSet.add(day);
                    }
                }
            }
        }
        
        return new ArrayList<>(daysSet);
    }

    private List<String> extractBinColors(String text) {
        List<String> colors = new ArrayList<>();
        String[] colorKeywords = {"Blue", "Green", "Brown", "Black", "Grey", "Gray", "Red", "Yellow", "Purple", "Burgundy", "Light grey", "Light gray"};
        
        for (String color : colorKeywords) {
            if (text.contains(color)) {
                colors.add(color);
            }
        }
        return colors;
    }

    private List<String> extractBinTypes(String lowerText) {
        List<String> types = new ArrayList<>();
        String[] typeKeywords = {"General waste", "Recyclables", "Food waste", "Garden waste", "Compost", "Cardboard", "Glass", "Plastic", "Paper"};
        
        for (String type : typeKeywords) {
            if (lowerText.contains(type.toLowerCase())) {
                types.add(type);
            }
        }
        return types;
    }

    private Map<String, String> extractLocation(Document doc, String pageText) {
        Map<String, String> location = new HashMap<>();
        
        if (doc == null) {
            return location;
        }
        
        // First, try to extract from the page heading (h1) which usually contains "Street, Area" or "Street, SubArea, Area"
        Element heading = doc.selectFirst("h1");
        if (heading != null) {
            String headingText = heading.text();
            if (headingText != null && !headingText.trim().isEmpty()) {
                headingText = headingText.trim();
                // Store the full heading text as-is
                location.put("fullLocation", headingText);
                
                // Also parse it for individual components
                String[] parts = headingText.split(",");
                if (parts.length >= 2) {
                    // First part is always the street
                    location.put("street", parts[0].trim());
                    // Last part is always the main area/town
                    location.put("area", parts[parts.length - 1].trim());
                    return location;
                } else if (parts.length == 1) {
                    location.put("street", parts[0].trim());
                }
            }
        }
        
        // Fallback: Extract from structured table data
        Elements tables = doc.select("table");
        if (tables != null) {
            for (Element table : tables) {
                Elements rows = table.select("tr");
                if (rows == null) continue;
                
                for (Element row : rows) {
                    Elements cells = row.select("td, th");
                    
                    if (cells != null && cells.size() >= 2) {
                        String label = cells.get(0).text();
                        String value = cells.get(1).text();
                        
                        if (label == null || value == null) continue;
                        
                        label = label.trim();
                        value = value.trim();
                        
                        // Look for known location labels
                        if (label.toLowerCase().contains("location") || label.toLowerCase().contains("address")) {
                            String[] addressParts = value.split(",");
                            if (addressParts.length >= 2) {
                                location.put("fullLocation", value);
                                location.put("street", addressParts[0].trim());
                                location.put("area", addressParts[addressParts.length - 1].trim());
                            } else if (addressParts.length == 1) {
                                location.put("street", addressParts[0].trim());
                            }
                        }
                        
                        // Extract area/town
                        if (label.toLowerCase().contains("area") || label.toLowerCase().contains("town")) {
                            location.put("area", value);
                        }
                    }
                }
            }
        }
        
        return location;
    }
}

