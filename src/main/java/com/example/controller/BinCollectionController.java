package com.example.controller;

import com.example.service.BinCollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class BinCollectionController {
    @Autowired
    private BinCollectionService binCollectionService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/check-bins")
    public String checkBins(@RequestParam String url, Model model) {
        Map<String, Object> rawData = binCollectionService.getBinCollectionInfo(url);
        BinCollectionService.BinCollectionData binData = binCollectionService.formatBinData(rawData);
        
        model.addAttribute("binData", binData);
        model.addAttribute("url", url);
        
        return "results";
    }

    @GetMapping("/api/bins")
    public String apiCheck(@RequestParam(defaultValue = "https://www.southlanarkshire.gov.uk/directory_record/574605/clincarthill_road_rutherglen") String url, Model model) {
        Map<String, Object> rawData = binCollectionService.getBinCollectionInfo(url);
        BinCollectionService.BinCollectionData binData = binCollectionService.formatBinData(rawData);
        
        model.addAttribute("binData", binData);
        model.addAttribute("url", url);
        
        return "results";
    }
}
