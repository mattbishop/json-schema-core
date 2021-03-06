/*
 * Copyright (c) 2013, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.ref;

import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonschema.exceptions.JsonReferenceException;
import com.github.fge.jsonschema.exceptions.unchecked.JsonReferenceError;
import net.jcip.annotations.Immutable;

import java.net.URI;
import java.net.URISyntaxException;

import static com.github.fge.jsonschema.messages.JsonReferenceErrors.*;

/**
 * Representation of a JSON Reference
 *
 * <p><a href="http://tools.ietf.org/html/draft-pbryan-zyp-json-ref-03">JSON
 * Reference</a>, currently a draft, is a way to define a path within a JSON
 * document.</p>
 *
 * <p>To quote the draft, "A JSON Reference is a JSON object, which contains
 * a member named "$ref", which has a JSON string value." This string value
 * must be a URI. Example:</p>
 *
 * <pre>
 *     {
 *         "$ref": "http://example.com/example.json#/foo/bar"
 *     }
 * </pre>
 *
 * <p>This class differs from the JSON Reference draft in that it accepts to
 * process illegal references, in the sense that they are URIs, but their
 * fragment parts are not JSON Pointers (in which case {@link #isLegal()}
 * returns {@code false}.</p>
 *
 * <p>The implementation is a wrapper over Java's {@link URI}, with the
 * following characteristics:</p>
 *
 * <ul>
 *     <li>all URIs are normalized from the get go;</li>
 *     <li>an empty fragment is equivalent to no fragment at all, and stands for
 *     a root JSON Pointer, as required by the draft;</li>
 *     <li>a reference is taken to be absolute if the underlying URI is absolute
 *     <i>and</i> it has no fragment, or an empty fragment.</li>
 * </ul>
 *
 * <p>It also special cases the following:</p>
 *
 * <ul>
 *     <li>an empty reference (for instance, used in anonymouns schemas);</li>
 *     <li>URIs with the {@code jar} scheme (the resolving algorithm differs --
 *     please note that this breaks URI resolution rules).</li>
 * </ul>
 *
 */
@Immutable
public abstract class JsonRef
{
    /**
     * The empty URI
     */
    private static final URI EMPTY_URI = URI.create("");

    /**
     * A "hash only" URI -- used by {@link EmptyJsonRef}
     */
    protected static final URI HASHONLY_URI = URI.create("#");

    /**
     * Whether this JSON Reference is legal
     */
    protected final boolean legal;

    /**
     * The URI, as provided by the input, with an appended empty fragment if
     * no fragment was provided
     */
    protected final URI uri;

    /**
     * The locator of this reference. This is the URI with an empty fragment
     * part.
     */
    protected final URI locator;

    /**
     * The pointer of this reference, if any
     *
     * <p>Initialized to null if the fragment part is not a JSON Pointer.</p>
     *
     * @see #isLegal()
     */
    protected final JsonPointer pointer;

    /**
     * String representation
     */
    private final String asString;

    /**
     * Hashcode
     */
    private final int hashCode;

    /**
     * Main constructor, {@code protected} by design
     *
     * @param uri the URI to build that reference
     */
    protected JsonRef(final URI uri)
    {
        final String scheme = uri.getScheme();
        final String ssp = uri.getSchemeSpecificPart();
        final String uriFragment = uri.getFragment();

        /*
         * Account for URIs with no fragment: substitute an empty one
         */
        final String realFragment = uriFragment == null ? "" : uriFragment;

        /*
         * Compute the fragment
         */
        boolean isLegal = true;
        JsonPointer ptr;
        try {
            ptr = realFragment.isEmpty() ? JsonPointer.empty()
                : new JsonPointer(realFragment);
        } catch (JsonPointerException ignored) {
            ptr = null;
            isLegal = false;
        }
        legal = isLegal;
        pointer = ptr;

        try {
            this.uri = new URI(scheme, ssp, realFragment);
            locator = new URI(scheme, ssp, "");
            asString = this.uri.toString();
            hashCode = asString.hashCode();
        } catch (URISyntaxException e) {
            /*
             * Can't happen: we did have a legal URI to start with
             */
            throw new RuntimeException("WTF??", e);
        }
    }

    /**
     * Build a JSON Reference from a URI
     *
     * @param uri the provided URI
     * @return the JSON Reference
     * @throws JsonReferenceError the provided URI is null
     */
    public static JsonRef fromURI(final URI uri)
    {
        NULL_URI.checkThat(uri != null);

        final URI normalized = uri.normalize();

        if (HASHONLY_URI.equals(normalized) || EMPTY_URI.equals(normalized))
            return EmptyJsonRef.getInstance();

        return "jar".equals(normalized.getScheme())
            ? new JarJsonRef(normalized)
            : new HierarchicalJsonRef(normalized);
    }

    /**
     * Build a JSON Reference from a string input
     *
     * @param s the string
     * @return the reference
     * @throws JsonReferenceException string is not a valid URI
     * @throws JsonReferenceError provided string is null
     */
    public static JsonRef fromString(final String s)
        throws JsonReferenceException
    {
        NULL_INPUT.checkThat(s != null);

        try {
            return fromURI(new URI(s));
        } catch (URISyntaxException e) {
            throw new JsonReferenceException(INVALID_URI.asMessage()
                .put("input", s), e);
        }
    }

    /**
     * Return an empty reference
     *
     * <p>An empty reference is a reference which only has an empty fragment.
     * </p>
     *
     * @return a statically allocated empty reference
     */
    public static JsonRef emptyRef()
    {
        return EmptyJsonRef.getInstance();
    }

    /**
     * Return the underlying URI for this JSON Reference
     *
     * @return the URI
     */
    public final URI toURI()
    {
        return uri;
    }

    /**
     * Tell whether this reference is an absolute reference
     *
     * <p>See description.</p>
     *
     * @return {@code true} if the JSON Reference is absolute
     */
    public abstract boolean isAbsolute();

    /**
     * Resolve this reference against another reference
     *
     * @param other the reference to resolve
     * @return the resolved reference
     */
    public abstract JsonRef resolve(final JsonRef other);

    /**
     * Return this JSON Reference's locator
     *
     * <p>This returns the reference with an empty fragment, ie the URI of the
     * document itself.</p>
     *
     * @return an URI
     */
    public final URI getLocator()
    {
        return locator;
    }

    /**
     * Tell whether this JSON Reference is legal
     *
     * <p>Recall: it is legal if and only if its fragment part is a JSON
     * pointer.</p>
     *
     * @return {@code true} if legal
     * @see JsonPointer
     */
    public final boolean isLegal()
    {
        return legal;
    }

    /**
     * Return the fragment part of this JSON Reference as a JSON Pointer
     *
     * <p>If the reference is not legal, this returns {@code null} <b>without
     * further notice</b>, so beware!</p>
     *
     * @return a JSON Pointer
     * @see JsonPointer
     */
    public final JsonPointer getPointer()
    {
        return pointer;
    }

    /**
     * Tell whether the current JSON Reference "contains" another
     *
     * <p>This is considered true iif both references have the same locator,
     * in other words, if they differ only by their fragment part.</p>
     *
     * @param other the other reference
     * @return see above
     */
    public final boolean contains(final JsonRef other)
    {
        return locator.equals(other.locator);
    }

    @Override
    public final int hashCode()
    {
        return hashCode;
    }

    @Override
    public final boolean equals(final Object obj)
    {
        if (obj == null)
            return false;
        if (this == obj)
            return true;

        if (!(obj instanceof JsonRef))
            return false;

        final JsonRef that = (JsonRef) obj;
        return asString.equals(that.asString);
    }

    @Override
    public final String toString()
    {
        return asString;
    }
}
