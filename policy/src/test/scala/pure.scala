package pure

import scala.annotation.unchecked._

package p {
  final class MetersSquared(val value: Double) extends AnyVal
  final class MeterOps(val value: Double) extends AnyVal {
    @inline def m: Meter      = new Meter(value)
    @inline def km: Kilometer = new Kilometer(value)
  }
  final class Kilometer(val value: Double) extends AnyVal
  final class Meter(val value: Double) extends AnyVal {
    @inline def *(that: Meter): MetersSquared = new MetersSquared(value * that.value)
  }

  @uncheckedPure object `package` {
    @inline final implicit def doubleUnits(x: Double): MeterOps = new MeterOps(x)
    @inline final implicit def kmToMeter(x: Kilometer): Meter   = new Meter(x.value * 1000)

    // scala 2.11.2              // policy
    //                           //
    //  0: getstatic     #25     //  0: dload_3
    //  3: getstatic     #30     //  1: sipush        1000
    //  6: astore        5       //  4: i2d
    //  8: dload_3               //  5: dmul
    //  9: sipush        1000    //  6: dstore        5
    // 12: i2d                   //  8: dload_1
    // 13: dmul                  //  9: dload         5
    // 14: dstore        7       // 11: dmul
    // 16: astore        6       // 12: dreturn
    // 18: dload_1
    // 19: dload         7
    // 21: dmul
    // 22: dreturn
    def calc(x: Meter, y: Double): MetersSquared = x * y.km
  }
}
