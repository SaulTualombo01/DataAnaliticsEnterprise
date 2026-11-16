package org.dataanaliticsent.TroncoComun.identidad.internal.dominio;
import java.time.Duration;
public record PoliticaInvitaciones(Duration invitationTtl,boolean manualDeliveryEnabled) { }
