

a = Array.immutable(...)
b = Array.mutable(...)


// -- LMS only
b(i) = a      // OK

c = b(i)      // OK

b(i)(j) = x   // ERROR - b(i) is not mutable!
c(j) = x      // ERROR - c is not mutable!
a(j)          // OK

b(i)(k) = y   // ERROR - b(i) is not mutable!
c(k)          // OK


// -- Delite AtomicRead + AtomicWrite
b(i) = a      // OK

c = b(i)      // OK

b(i)(j) = x   // OK - creates NestedAtomicWrite(b, List(i), ArrayUpdate(_, j, x))
c(j) = x      // OK - creates NestedAtomicWrite(b, List(i), ArrayUpdate(_, j, x))

a(j)          // Problem - may be reordered w.r.t. writes on b

b(i)(k) = y   // OK - creates NestedAtomicWrite(b, List(i), ArrayUpdate(_, k, y))
c(k)          // OK - creates NestedAtomicRead(b, List(i), ArrayApply(_, k))


// -- Delite AtomicWrite + AtomicRead
