package com.gemform.be.echoes.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, Object> hello(@RequestParam(defaultValue = "Echoes") String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("message", "Hello, " + name);
        body.put("serverTime", LocalDateTime.now().toString());
        return body;
    }
}
