package me.hejl.gramps.model;

/** Rectangle within an image, in percent (0-100) of its width and height. */
public record Region(int x1, int y1, int x2, int y2) {}
