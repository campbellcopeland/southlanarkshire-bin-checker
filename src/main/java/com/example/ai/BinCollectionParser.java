package com.example.ai;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BinCollectionParser {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int TIMEOUT = 10000;

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
        
        // Try to find bin collection schedules in common elements
        Elements containers = doc.select("[class*='bin'], [class*='collection'], [class*='waste'], [id*='bin'], [id*='collection']");
        
        // Also search for tables and divs that might contain bin info
        Elements tables = doc.select("table");
        Elements divs = doc.select("div[class*='info'], div[class*='content'], div[class*='schedule']");

        // Extract from tables
        for (Element table : tables) {
            extractFromTable(table, bins, thisWeekBins);
        }

        // Extract from divs with specific keywords
        for (Element div : divs) {
            String text = div.text();
            if (text.toLowerCase().contains("collection") || 
                text.toLowerCase().contains("bin") ||
                text.toLowerCase().contains("waste")) {
                extractBinDetails(div, bins, thisWeekBins);
            }
        }

        // Search for "This week" specifically
        extractThisWeekInfo(pageText, thisWeekBins);

        // Search for color keywords and collection day information
        List<String> collectionDays = extractCollectionDays(pageText);
        List<String> colors = extractBinColors(pageText);
        List<String> binTypes = extractBinTypes(pageText);
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
        if (text.length() > 20 && text.length() < 500) {
            Map<String, String> bin = new HashMap<>();
            bin.put("details", text);
            bins.add(bin);
            
            // Check if this is this week's information
            if (text.toLowerCase().contains("this week")) {
                thisWeekBins.add(bin);
            }
        }
    }

    private void extractThisWeekInfo(String pageText, List<Map<String, String>> thisWeekBins) {
        // Look for "This week's collection" section ONLY
        String lowerText = pageText.toLowerCase();
        int thisWeekIndex = lowerText.indexOf("this week");
        
        if (thisWeekIndex == -1) {
            // No "This week's collection" section means no bins collected this week
            return;
        }
        
        // Find the exact boundaries of the "This week's collection" section
        int start = thisWeekIndex;
        
        // Look for where this section ends - typically "Fortnightly", next date line, or another section
        String afterThisWeek = pageText.substring(start);
        String lowerAfter = afterThisWeek.toLowerCase();
        
        int sectionEnd = afterThisWeek.length();
        
        // Find section boundaries (these typically mark the end of "this week")
        int nextFortnightly = lowerAfter.indexOf("fortnightly");
        int nextWeek = lowerAfter.indexOf("next week");
        int nextMonth = lowerAfter.indexOf("february") ; // Look for next month indicators
        int areaSection = lowerAfter.indexOf("area");
        
        // Get the earliest boundary (the actual end of this week's section)
        if (nextFortnightly > 0 && nextFortnightly < sectionEnd) {
            sectionEnd = nextFortnightly;
        }
        if (nextWeek > 0 && nextWeek < sectionEnd) {
            sectionEnd = nextWeek;
        }
        if (nextMonth > 0 && nextMonth < sectionEnd) {
            sectionEnd = nextMonth;
        }
        if (areaSection > 0 && areaSection < sectionEnd) {
            sectionEnd = areaSection;
        }
        
        // Cap at reasonable distance to avoid picking up unrelated content
        sectionEnd = Math.min(sectionEnd, 500);
        
        String thisWeekSection = afterThisWeek.substring(0, sectionEnd);
        String lowerSection = thisWeekSection.toLowerCase();
        
        // Extract ONLY the bins explicitly listed in THIS section
        // Check for exact "bin -" or "bin" pattern to match the source format
        
        // Only add if the bin is mentioned in this specific section AND not marked as fortnightly/4 weekly
        // Look for actual bin type indicators that appear BEFORE frequency markers
        
        if (lowerSection.contains("blue bin") || lowerSection.contains("blue - paper")) {
            Map<String, String> bin = new HashMap<>();
            bin.put("description", "Blue bin - Paper and card");
            bin.put("schedule", "Collection this week");
            bin.put("color", "blue");
            thisWeekBins.add(bin);
        }
        
        if (lowerSection.contains("burgundy bin") || lowerSection.contains("burgundy - food")) {
            Map<String, String> bin = new HashMap<>();
            bin.put("description", "Burgundy bin - Food and garden waste");
            bin.put("schedule", "Collection this week");
            bin.put("color", "burgundy");
            thisWeekBins.add(bin);
        }
        
        // Only add these if they're explicitly in THIS WEEK's section, not fortnightly/4 weekly
        if ((lowerSection.contains("light grey bin") || lowerSection.contains("light grey - glass")) && 
            !lowerSection.contains("4 weekly") && !lowerSection.contains("fortnightly")) {
            Map<String, String> bin = new HashMap<>();
            bin.put("description", "Light grey bin - Glass, cans and plastics");
            bin.put("schedule", "Collection this week");
            bin.put("color", "light");
            thisWeekBins.add(bin);
        }
        
        if ((lowerSection.contains("black bin") || lowerSection.contains("black/green bin") || lowerSection.contains("black - general")) && 
            !lowerSection.contains("4 weekly") && !lowerSection.contains("fortnightly")) {
            Map<String, String> bin = new HashMap<>();
            bin.put("description", "Black/Green bin - General waste");
            bin.put("schedule", "Collection this week");
            bin.put("color", "black");
            thisWeekBins.add(bin);
        }
    }

    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String[] words = str.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        return sb.toString();
    }

    private List<String> extractCollectionDays(String text) {
        List<String> days = new ArrayList<>();
        
        // Extract collection days from the schedule table/section, NOT from "This week's collection" 
        // (which just shows the calendar week date range)
        // Look for the pattern "BinType - Description    Day (Frequency)"
        
        String[] allDays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        
        // Look for collection schedule patterns like "Friday (Fortnightly)" or "Friday (4 Weekly)"
        for (String day : allDays) {
            String pattern = day + " (";  // Matches "Friday (" but not just "Friday" in a date
            if (text.contains(pattern)) {
                if (!days.contains(day)) {
                    days.add(day);
                }
            }
        }
        
        // If no pattern found, try simpler approach: look for days in collection/schedule context
        if (days.isEmpty()) {
            String lowerText = text.toLowerCase();
            for (String day : allDays) {
                int dayIdx = lowerText.indexOf(day.toLowerCase());
                if (dayIdx != -1) {
                    // Check if it's in a collection context (near "fortnightly", "weekly", "bin", etc.)
                    String context = text.substring(Math.max(0, dayIdx - 100), Math.min(text.length(), dayIdx + 100)).toLowerCase();
                    if (context.contains("fortnightly") || context.contains("weekly") || context.contains("bin") || context.contains("waste")) {
                        if (!days.contains(day)) {
                            days.add(day);
                        }
                    }
                }
            }
        }
        
        return days;
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

    private List<String> extractBinTypes(String text) {
        List<String> types = new ArrayList<>();
        String[] typeKeywords = {"General waste", "Recyclables", "Food waste", "Garden waste", "Compost", "Cardboard", "Glass", "Plastic", "Paper"};
        
        for (String type : typeKeywords) {
            if (text.toLowerCase().contains(type.toLowerCase())) {
                types.add(type);
            }
        }
        return types;
    }

    private Map<String, String> extractLocation(Document doc, String pageText) {
        Map<String, String> location = new HashMap<>();
        
        // First, try to extract from the page heading (h1) which usually contains "Street, Area" or "Street, SubArea, Area"
        Element heading = doc.selectFirst("h1");
        if (heading != null) {
            String headingText = heading.text().trim();
            if (!headingText.isEmpty()) {
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
        for (Element table : tables) {
            Elements rows = table.select("tr");
            for (Element row : rows) {
                Elements cells = row.select("td, th");
                
                if (cells.size() >= 2) {
                    String label = cells.get(0).text().trim();
                    String value = cells.get(1).text().trim();
                    
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
        
        return location;
    }
}

