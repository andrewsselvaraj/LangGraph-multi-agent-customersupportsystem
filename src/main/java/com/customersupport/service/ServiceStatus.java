package com.customersupport.service;

import org.springframework.stereotype.Service;

/** Stand-in for a status page integration. */
@Service
public class ServiceStatus {

    private volatile String status = "All systems operational.";

    public String current() {
        return status;
    }

    public void set(String status) {
        this.status = status;
    }
}
