package com.sumirelabs.kita.common;

/** Versioned plugin boundary; positions describe consumed output, not decoded-ahead audio. */
public record CrossfadeState(String id, String token, long position, String next, boolean fading, boolean done, String error) {}
