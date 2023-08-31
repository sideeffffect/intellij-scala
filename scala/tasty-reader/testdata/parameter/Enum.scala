package parameter

trait Enum {
  enum TypeParameter[A] {
    case Case extends TypeParameter[Int]
  }

  enum TypeParameters[A, B] {
    case Case extends TypeParameters[Int, Long]
  }

  enum ValueParameter(x: Int) {
    case Case/**/ extends ValueParameter(1)/**/
  }

  enum ValueParameters(x: Int, y: Long) {
    case Case/**/ extends ValueParameters(1, 2L)/**/
  }

  enum EmptyClause/**/()/**/ {
    case Case/**/ extends EmptyClause/**/
  }

  enum MultipleClauses(x: Int)(y: Long) {
    case Case/**/ extends MultipleClauses(1)(2L)/**/
  }

  enum MultipleClausesEmpty1()(y: Long) {
    case Case/**/ extends MultipleClausesEmpty1()(2L)/**/
  }

  enum MultipleClausesEmpty2(x: Int)() {
    case Case/**/ extends MultipleClausesEmpty2(1)()/**/
  }

  enum MultipleClausesEmpty3()() {
    case Case/**/ extends MultipleClausesEmpty3()()/**/
  }

  enum TypeAndValueParameters[A](x: Int) {
    case Case extends TypeAndValueParameters[Int]/**/(1)/**/
  }
}