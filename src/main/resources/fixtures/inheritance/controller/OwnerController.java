package com.example.inheritance.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/owners")
public class OwnerController {

    @GetMapping("/{id}")
    public Owner findById(@PathVariable Integer id) {
        return null;
    }
}