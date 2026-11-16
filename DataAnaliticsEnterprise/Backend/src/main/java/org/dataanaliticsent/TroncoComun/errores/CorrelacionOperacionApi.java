package org.dataanaliticsent.TroncoComun.errores;
import java.util.UUID;

/** Correlación del servidor para auditoría; no representa identidad ni autorización. */
public interface CorrelacionOperacionApi { UUID actual(); }
