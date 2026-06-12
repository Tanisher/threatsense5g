package com.threatsense.controller;

import com.threatsense.dto.TrafficIngestionResult;
import com.threatsense.dto.TrafficUploadSummaryDto;
import com.threatsense.model.NetworkTraffic;
import com.threatsense.service.TrafficIngestionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/traffic")
public class TrafficController {

    private final TrafficIngestionService trafficIngestionService;

    public TrafficController(TrafficIngestionService trafficIngestionService) {
        this.trafficIngestionService = trafficIngestionService;
    }

    @GetMapping("/upload")
    public String showUploadForm() {
        return "traffic/upload";
    }

    @PostMapping("/upload")
    public String handleUpload(@RequestParam("file") MultipartFile file,
                               @AuthenticationPrincipal UserDetails principal,
                               RedirectAttributes redirectAttributes) {
        String username = principal != null ? principal.getUsername() : "unknown";

        TrafficIngestionResult result = trafficIngestionService.parseAndSaveCSV(file, username);

        if (!result.getValidationErrors().isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    String.join("; ", result.getValidationErrors())
            );
        } else {
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Uploaded " + result.getRowCount() + " traffic records successfully."
            );
        }

        return "redirect:/traffic/history";
    }

    @GetMapping("/history")
    public String showUploadHistory(Model model) {
        List<TrafficUploadSummaryDto> history = trafficIngestionService.getUploadHistory();
        model.addAttribute("history", history);
        return "traffic/history";
    }

    @GetMapping("/{id}")
    public String showTrafficDetail(@PathVariable Long id,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        try {
            NetworkTraffic traffic = trafficIngestionService.getTrafficById(id);
            model.addAttribute("traffic", traffic);
            return "traffic/detail";
        } catch (java.util.NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("error", "Traffic record not found.");
            return "redirect:/traffic/history";
        }
    }

    @GetMapping
    public String listBySource(@RequestParam("source") String source, Model model) {
        List<NetworkTraffic> records = trafficIngestionService.getTrafficByUploadSource(source);
        model.addAttribute("records", records);
        model.addAttribute("source", source);
        return "traffic/list";
    }
}

