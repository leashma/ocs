package edu.gemini.spModel.core

import edu.gemini.spModel.core.WavelengthConversions._
import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Arbitrary._

import scalaz.==>>

trait Arbitraries {

  implicit val arbAngle: Arbitrary[Angle] =
    Arbitrary(arbitrary[Short].map(n => n / 10.0).map(Angle.fromDegrees))

  implicit val arbP: Arbitrary[OffsetP] = Arbitrary { arbitrary[Angle].map(OffsetP(_)) }
  implicit val arbQ: Arbitrary[OffsetQ] = Arbitrary { arbitrary[Angle].map(OffsetQ(_)) }

  implicit val arbOffset: Arbitrary[Offset] =
    Arbitrary {
      for {
        p <- arbitrary[OffsetP]
        q <- arbitrary[OffsetQ]
      } yield Offset(p, q)
    }

  implicit val arbRA: Arbitrary[RightAscension] =
    Arbitrary(arbitrary[Angle].map(RightAscension.fromAngle))

  implicit val arbDec: Arbitrary[Declination] =
    Arbitrary(arbitrary[Angle].map(Declination.zero.offset(_)._1))

  implicit val arbRAVelocity: Arbitrary[RightAscensionAngularVelocity] =
    // Velocity can be any number but it makes sense to restrict to work with the legacy model
    Arbitrary(arbitrary[Double].map(v => RightAscensionAngularVelocity(AngularVelocity(v % AngularVelocity.MilliArcSecsInADegree))))

  implicit val arbDecVelocity: Arbitrary[DeclinationAngularVelocity] =
    // Velocity can be any number but it makes sense to restrict to work with the legacy model
    Arbitrary(arbitrary[Double].map(v => DeclinationAngularVelocity(AngularVelocity(v % AngularVelocity.MilliArcSecsInADegree))))

  implicit val arbCoords: Arbitrary[Coordinates] =
    Arbitrary {
      for {
        ra  <- arbitrary[RightAscension]
        dec <- arbitrary[Declination]
      } yield Coordinates(ra, dec)
    }

  // implicit val arbEphemerisElement: Arbitrary[(Long, Coordinates)] =
  //   Arbitrary {
  //     for {
  //       coords <- arbitrary[Coordinates]
  //       valid  <- arbitrary[Long]
  //     } yield (coords, valid)
  //   }

  implicit val arbMagnitudeBand: Arbitrary[MagnitudeBand] =
    Arbitrary(oneOf(MagnitudeBand.all))

  implicit val arbMagnitudeSystem: Arbitrary[MagnitudeSystem] =
    Arbitrary(oneOf(MagnitudeSystem.all))

  implicit val arbEpoch: Arbitrary[Epoch] =
    Arbitrary {
      for {
        d <- arbitrary[Short].map(_ / 100.0)
      } yield Epoch(d)
    }

  implicit val arbProperMotion: Arbitrary[ProperMotion] =
    Arbitrary {
      for {
        deltaRA  <- arbitrary[RightAscensionAngularVelocity]
        deltaDec <- arbitrary[DeclinationAngularVelocity]
        epoch    <- arbitrary[Epoch]
        parallax <- arbitrary[Option[Angle]]
        rv       <- arbitrary[Option[Double]]
      } yield ProperMotion(deltaRA, deltaDec, epoch, parallax, rv)
    }

  implicit val arbMagnitude: Arbitrary[Magnitude] =
    Arbitrary {
      for {
        value  <- arbitrary[Double]
        band   <- arbitrary[MagnitudeBand]
        error  <- arbitrary[Option[Double]]
        system <- arbitrary[MagnitudeSystem]
      } yield Magnitude(value, band, error, system)
    }

  implicit val arbTooTarget: Arbitrary[Target.TooTarget] =
    Arbitrary(arbitrary[String].map(Target.TooTarget))

  implicit val arbHorizonsDesignation: Arbitrary[HorizonsDesignation] =
    Arbitrary {
      for {
        objectName <- arbitrary[Int]
      } yield HorizonsDesignation.Asteroid(objectName.toString)
    }

  implicit val arbSiderealTarget: Arbitrary[Target.SiderealTarget] =
    Arbitrary {
      for {
          name         <- arbitrary[String]
          coordinates  <- arbitrary[Coordinates]
          properMotion <- arbitrary[Option[ProperMotion]]
          magnitudes   <- arbitrary[List[Magnitude]]
      } yield Target.SiderealTarget(name, coordinates, properMotion, magnitudes)
    }

  implicit val arbNonSiderealTarget: Arbitrary[Target.NonSiderealTarget] =
    Arbitrary {
      for {
         name         <- arbitrary[String]
         ephemeris    <- arbitrary[List[(Long, Coordinates)]]
         horizonsDesignation <- arbitrary[Option[HorizonsDesignation]]
      } yield Target.NonSiderealTarget(name, ==>>.fromList(ephemeris), horizonsDesignation)
    }

  implicit val arbTarget: Arbitrary[Target] =
    Arbitrary(oneOf(
      arbitrary[Target.TooTarget],
      arbitrary[Target.SiderealTarget],
      arbitrary[Target.NonSiderealTarget]))

  implicit val arbWavelength: Arbitrary[Wavelength] =
    Arbitrary(arbitrary[Short].map(n => Math.abs(n).nm))

}


