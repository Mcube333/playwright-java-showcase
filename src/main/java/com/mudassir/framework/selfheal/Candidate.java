package com.mudassir.framework.selfheal;

import java.util.ArrayList;
import java.util.List;

/** A visible element on the current page that could replace a broken locator. */
public class Candidate {
    public int idx;
    public ElementFingerprint fp;
    /** Selectors for this element, most stable first (test id, name, aria-label, ..., CSS path). */
    public List<String> selectors = new ArrayList<>();
}
