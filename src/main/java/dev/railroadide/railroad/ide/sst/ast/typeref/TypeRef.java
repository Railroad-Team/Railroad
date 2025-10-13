package dev.railroadide.railroad.ide.sst.ast.typeref;

import dev.railroadide.railroad.ide.sst.ast.AstNode;

public sealed interface TypeRef extends AstNode permits ArrayTypeRef, ClassOrInterfaceTypeRef, IntersectionTypeRef,
        PrimitiveTypeRef, SugarTypeRef, TypeDiamond, UnionTypeRef, WildcardTypeRef {}