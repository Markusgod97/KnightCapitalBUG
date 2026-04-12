package com.knightcapital.fixed;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * SMARS - Smart Market Access Routing System
 * Versión CORREGIDA - Cumple Quality Gate de SonarQube
 *
 * CORRECCIONES APLICADAS:
 *  [BLOCKER → FIXED]  Código PowerPeg eliminado completamente del codebase
 *  [BLOCKER → FIXED]  Enum tipado reemplaza magic number ambiguo
 *  [BLOCKER → FIXED]  AtomicBoolean garantiza visibilidad entre hilos
 *  [MAJOR   → FIXED]  Manejo de excepción con compensación explícita
 *  [MINOR   → FIXED]  Método largo refactorizado en métodos pequeños
 */
public class OrderRouter {

    private static final Logger log = Logger.getLogger(OrderRouter.class.getName());

    // =========================================================
    // CORRECCIÓN #1 - Enum tipado en lugar de magic number
    // PowerPeg ya NO existe en este codebase. Fue eliminada
    // completamente con "git rm" antes de este deploy.
    // Ahora cada estrategia tiene un nombre descriptivo.
    // =========================================================
    public enum RoutingStrategy {
        SMARS,   // Smart Market Access Routing (2012)
        DIRECT,  // Envío directo al exchange
        LIMIT    // Orden limitada con precio máximo
        // PowerPeg fue retirada en 2005. No existe aquí.
    }

    // =========================================================
    // CORRECCIÓN #2 - AtomicBoolean para visibilidad entre hilos
    // AtomicBoolean garantiza operaciones atómicas y visibilidad
    // inmediata en todos los hilos (Java Memory Model).
    // Antes: 'static boolean' podía quedar cacheado en CPU.
    // =========================================================
    private final AtomicBoolean routingEnabled = new AtomicBoolean(true);

    private final CircuitBreaker circuitBreaker;

    public OrderRouter(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public void routeOrder(Order order) {

        // Verificación thread-safe del estado del sistema
        if (!routingEnabled.get()) {
            log.info("Routing deshabilitado. Orden descartada: " + order.getId());
            return;
        }

        // Circuit breaker activo antes de ejecutar
        if (circuitBreaker.isTripped()) {
            log.warning("Circuit breaker activo. Orden bloqueada: " + order.getId());
            return;
        }

        // =========================================================
        // CORRECCIÓN #3 - Switch con enum tipado
        // El compilador detecta casos no manejados.
        // Es imposible activar código legacy accidentalmente.
        // =========================================================
        switch (order.getStrategy()) {
            case SMARS:
                executeSmars(order);
                break;
            case DIRECT:
                executeDirect(order);
                break;
            case LIMIT:
                executeLimit(order);
                break;
            default:
                // Nunca debería ocurrir, pero si ocurre: falla ruidosamente
                throw new UnsupportedOperationException(
                    "Estrategia no soportada: " + order.getStrategy()
                );
        }
    }

    // =========================================================
    // CORRECCIÓN #4 - Manejo de excepción con compensación
    // Ante un error: se registra, se notifica, y se activa
    // el circuit breaker si supera el umbral de tolerancia.
    // =========================================================
    private void executeSmars(Order order) {
        try {
            MarketExecutor.execute(order);
            log.info("Orden SMARS ejecutada: " + order.getSymbol());
        } catch (TradingException e) {
            log.severe("Fallo en ejecución SMARS [" + order.getId() + "]: " + e.getMessage());
            circuitBreaker.recordFailure();          // cuenta el fallo
            PositionTracker.compensate(order);       // deshace la posición parcial
            AlertService.notifyOps("Fallo SMARS - revisar: " + order.getId());
        }
    }

    private void executeDirect(Order order) {
        try {
            MarketExecutor.executeDirect(order);
        } catch (TradingException e) {
            log.severe("Fallo Direct [" + order.getId() + "]: " + e.getMessage());
            circuitBreaker.recordFailure();
        }
    }

    private void executeLimit(Order order) {
        try {
            MarketExecutor.executeLimit(order);
        } catch (TradingException e) {
            log.severe("Fallo Limit [" + order.getId() + "]: " + e.getMessage());
            circuitBreaker.recordFailure();
        }
    }

    // =========================================================
    // CORRECCIÓN #5 - Método corto con responsabilidad única
    // La validación está separada de la ejecución.
    // =========================================================
    public void processOrderBatch(List<Order> orders) {
        orders.stream()
              .filter(this::isValid)
              .forEach(this::routeOrder);
    }

    private boolean isValid(Order order) {
        if (order == null)                    return false;
        if (order.getSymbol() == null)        return false;
        if (order.getQuantity() <= 0)         return false;
        if (order.getStrategy() == null)      return false;
        return true;
    }

    public void disableRouting() {
        routingEnabled.set(false);
        log.warning("Routing de órdenes DESHABILITADO manualmente");
    }
}