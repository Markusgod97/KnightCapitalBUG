package com.knightcapital.buggy;
 
import java.util.logging.Logger;
 
/**
 * SMARS - Smart Market Access Routing System
 * Versión con errores - Replica los fallos del desastre Knight Capital (01/08/2012)
 *
 * ISSUES DETECTADOS POR SONARQUBE:
 *  [BLOCKER]  Línea 24  - Código heredado nunca eliminado (PowerPeg)
 *  [BLOCKER]  Línea 47  - Flag reutilizado con semántica ambigua (magic number)
 *  [BLOCKER]  Línea 58  - Variable de estado sin volatile (race condition)
 *  [MAJOR]    Línea 72  - Sin rollback ante excepción en ruta crítica
 *  [MINOR]    Línea 85  - Método demasiado largo (> 30 líneas)
 */
public class OrderRouter {
 
    private static final Logger log = Logger.getLogger(OrderRouter.class.getName());
 
    // =========================================================
    // BUG #1 [BLOCKER] - SonarQube: java:S1854 / Dead Code
    // El flag "1" fue usado por PowerPeg en 2003. En 2012 fue
    // reutilizado para SMARS, pero el código antiguo NUNCA se
    // eliminó. El servidor sin actualizar ejecutó PowerPeg.
    // =========================================================
    private static final int POWER_PEG_FLAG = 1;  // ← magic number ambiguo
    private PowerPegExecutor powerPegExecutor = new PowerPegExecutor();
 
    // =========================================================
    // BUG #2 [BLOCKER] - SonarQube: java:S2696 / Race condition
    // Variable compartida entre hilos SIN volatile ni AtomicBoolean.
    // El hilo de trading puede no ver el cambio del operador.
    // =========================================================
    private static boolean routingEnabled = true;  // ← falta volatile
 
    public void routeOrder(Order order) {
 
        // =====================================================
        // BUG #3 [BLOCKER] - SonarQube: java:S1854 / Dead code
        // Este bloque debería haber sido eliminado cuando
        // PowerPeg fue retirado. No lo fue. El servidor sin
        // actualizar lo ejecutó en bucle durante 45 minutos.
        // =====================================================
        if (order.getFlag() == POWER_PEG_FLAG) {
            log.warning("Activando PowerPeg (codigo LEGACY de 2003!)");
            powerPegExecutor.executeLegacy(order);  // ← compra alto, vende bajo en bucle
            return;
        }
 
        // Lógica SMARS (nueva - 2012)
        if (!routingEnabled) {  // ← puede no ver el cambio de otro hilo
            log.info("Routing deshabilitado, descartando orden");
            return;
        }
 
        // =====================================================
        // BUG #4 [MAJOR] - SonarQube: java:S2221 / Sin rollback
        // Las excepciones solo se loguean. No hay compensación
        // de posición ni detención del sistema.
        // =====================================================
        try {
            MarketExecutor.execute(order);
            log.info("Orden ejecutada: " + order.getSymbol());
        } catch (TradingException e) {
            log.severe("Error ejecutando orden: " + e.getMessage());
            // ← SonarQube: "Exception is caught but not rethrown,
            //   not handled, and no compensating action is taken"
            // No hay: rollback, compensación, ni circuit breaker
        }
    }
 
    // =========================================================
    // BUG #5 [MAJOR] - SonarQube: java:S138 / Método muy largo
    // Este método tiene 45 líneas. Límite recomendado: 30.
    // Dificulta el mantenimiento y revisión de código.
    // =========================================================
    public void processOrderBatch(java.util.List<Order> orders) {
        for (Order o : orders) {
            if (o == null) continue;
            if (o.getSymbol() == null) {
                log.warning("Simbolo nulo, saltando");
                continue;
            }
            if (o.getQuantity() <= 0) {
                log.warning("Cantidad invalida: " + o.getQuantity());
                continue;
            }
            routeOrder(o);
            // ... 20 líneas más de validación inline ...
        }
    }
}