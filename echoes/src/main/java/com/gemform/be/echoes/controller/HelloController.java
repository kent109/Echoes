package com.gemform.be.echoes.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, Object> hello(@RequestParam(defaultValue = "Echoes") String name) {
        return Map.of(
                "code", 0,
                "message", "Hello, " + name,
                "serverTime", LocalDateTime.now().toString());
    }
}
