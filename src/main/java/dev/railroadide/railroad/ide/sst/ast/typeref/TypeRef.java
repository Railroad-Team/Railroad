package dev.railroadide.railroad.ide.sst.ast.typeref;

import dev.railroadide.railroad.ide.sst.ast.AstNode;

/**
 * A reference to a Java type in source syntax.
 */
public sealed interface TypeRef extends AstNode permits ArrayTypeRef, ClassOrInterfaceTypeRef, IntersectionTypeRef,
    PrimitiveTypeRef, SugarTypeRef, TypeDiamond, UnionTypeRef, WildcardTypeRef {
}
