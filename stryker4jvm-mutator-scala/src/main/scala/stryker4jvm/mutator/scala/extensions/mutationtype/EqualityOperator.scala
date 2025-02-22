package stryker4jvm.mutator.scala.extensions.mutationtype

import scala.meta.Term

case object GreaterThan extends EqualityOperator {
  override val tree: Term.Name = Term.Name(">")
}

case object GreaterThanEqualTo extends EqualityOperator {
  override val tree: Term.Name = Term.Name(">=")
}

case object LesserThanEqualTo extends EqualityOperator {
  override val tree: Term.Name = Term.Name("<=")
}

case object LesserThan extends EqualityOperator {
  override val tree: Term.Name = Term.Name("<")
}

case object EqualTo extends EqualityOperator {
  override val tree: Term.Name = Term.Name("==")
}

case object NotEqualTo extends EqualityOperator {
  override val tree: Term.Name = Term.Name("!=")
}

case object TypedEqualTo extends EqualityOperator {
  override val tree: Term.Name = Term.Name("===")
}

case object TypedNotEqualTo extends EqualityOperator {
  override val tree: Term.Name = Term.Name("=!=")
}
