package com.thiago.escenasFX.service;

import org.apache.http.HttpResponse;

import nl.martijndwars.webpush.Notification;

/**
 * Separa el envío real (nl.martijndwars.webpush.PushService.send, que hace una llamada HTTP de
 * verdad) de PushService, para poder simularlo en los tests sin red — igual en espíritu a cómo
 * PushServiceTest ya simula el push sin contenido con MockRestServiceServer.
 */
@FunctionalInterface
interface WebPushClient {
    HttpResponse enviar(Notification notification) throws Exception;
}
