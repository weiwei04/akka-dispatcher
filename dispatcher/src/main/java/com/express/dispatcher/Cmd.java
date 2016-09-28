/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/16/15.
 */

package com.express.dispatcher;

public class Cmd {

    public final static class Dispatch {

        public static final String CMD = "dispatch";

        private final String id;

        public Dispatch(String id) { this.id = id; }

        public String getId() { return id; }
    }

    public final static class Cancel {

        public static final String CMD = "cancel";

        private final String id;

        public Cancel(String id) { this.id = id; }

        public String getId() { return id; }
    }

    public final static class Monitor {

        public static final String CMD = "monitor";

        public Monitor() { }
    }
}
