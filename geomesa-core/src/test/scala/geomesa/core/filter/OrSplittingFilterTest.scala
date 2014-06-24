package geomesa.core.filter

import org.geotools.factory.CommonFactoryFinder
import org.geotools.filter.text.ecql.ECQL
import org.junit.runner.RunWith
import org.opengis.filter._
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

object OrSplittingFilterTest {
  val ff = CommonFactoryFinder.getFilterFactory2

  implicit class RichFilter(val filter: Filter) {
    def &&(that: RichFilter) = ff.and(filter, that.filter)
    def ||(that: RichFilter) = ff.or(filter, that.filter)

    def &&(that: Filter) = ff.and(filter, that)
    def ||(that: Filter) = ff.or(filter, that)
    def ! = ff.not(filter)
  }

  implicit def stringToFilter(s: String) = ECQL.toFilter(s)
  def intToAttributeFilter(i: Int): Filter = s"attr$i = val$i"
  implicit def intToFilter(i: Int): RichFilter = intToAttributeFilter(i)

  val geom1: Filter = "INTERSECTS(geomesa_index_geometry, POLYGON ((41 28, 42 28, 42 29, 41 29, 41 28)))"
  val geom2: Filter = "INTERSECTS(geomesa_index_geometry, POLYGON ((44 23, 46 23, 46 25, 44 25, 44 23)))"
  val date1: Filter = "(geomesa_index_start_time between '0000-01-01T00:00:00.000Z' AND '9999-12-31T23:59:59.000Z')"
}

import OrSplittingFilterTest._

@RunWith(classOf[JUnitRunner])
class OrSplittingFilterTest extends Specification {
  val osf = new OrSplittingFilter
  def splitFilter(f: Filter) = osf.visit(f, null)

  "The OrSplittingFilter" should {

    "not do anything to filters without a top-level OR" in {
      val filterStrings = Seq(geom1, geom1 && date1, 1 && 2, (3 && 4).!, (1 || 3).!)

      filterStrings.foreach { f =>
        Seq(f) mustEqual splitFilter(f)
      }
    }

    "split an OR into two pieces" in {
      val orStrings = Seq(geom1 || geom2, geom2 || date1, 1 || 2, geom1 || 3)

      orStrings.foreach { f =>
        splitFilter(f).size mustEqual 2
      }
    }

    "recursively split nested ORs" in {
      val nested = Seq((geom1 || date1) || geom2, 1 || 2 || 3, 1 || (2 && 3) || 4, 1 || (geom2 || date1))
      nested.foreach { f =>
        splitFilter(f).size mustEqual 3
      }
    }

    "not run through lower-level filters" in {
      val filter = (3 || 4).! || (1 && 2)
      splitFilter(filter).size mustEqual 2
    }
  }
}