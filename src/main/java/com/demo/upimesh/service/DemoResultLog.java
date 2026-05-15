package com.demo.upimesh.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DemoResultLog {

    private List<IngestionResult> lastResults = new ArrayList<>();

    public synchronized List<IngestionResult> getLastResults() {
        return List.copyOf(lastResults);
    }

    public synchronized void setLastResults(List<IngestionResult> results) {
        this.lastResults = new ArrayList<>(results);
    }

    public synchronized void addResult(IngestionResult result) {
        this.lastResults = new ArrayList<>(List.of(result));
    }

    public synchronized void clear() {
        this.lastResults = new ArrayList<>();
    }
}
