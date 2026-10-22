/**
 * One WebSocket per client at {@code /ws}. Requests are {@code {id, type, payload}} and are answered with
 * {@code {type: "response", id, ok, data | error}}; server pushes are {@code {type: "event", event, data}}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Realtime")
package com.afrismart.realtime;
