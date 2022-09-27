/*
 * Copyright 2013-2022 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.sbe.ir;

import org.agrona.Verify;

import java.util.function.Supplier;

import static uk.co.real_logic.sbe.ir.Encoding.Presence.CONSTANT;
import static uk.co.real_logic.sbe.ir.Encoding.Presence.OPTIONAL;

/**
 * Class to encapsulate a token of information for the message schema stream. This Intermediate Representation (IR)
 * is intended to be language, schema, platform independent.
 * <p>
 * Processing and optimization could be run over a list of Tokens to perform various functions
 * <ul>
 * <li>re-ordering of fields based on encodedLength</li>
 * <li>padding of fields in order to provide expansion room</li>
 * <li>computing offsets of individual fields</li>
 * <li>etc.</li>
 * </ul>
 * <p>
 * IR could be used to generate code or other specifications. It should be possible to do the
 * following:
 * <ul>
 * <li>generate a FIX/SBE schema from IR</li>
 * <li>generate an ASN.1 spec from IR</li>
 * <li>generate a GPB spec from IR</li>
 * <li>etc.</li>
 * </ul>
 * <p>
 * IR could be serialized to storage or network via code generated by SBE. Then read back in to
 * a List of {@link Token}s.
 * <p>
 * The entire IR of an entity is a {@link java.util.List} of {@link Token} objects. The order of this list is very
 * important. Encoding of fields is done by nodes pointing to specific encoding
 * {@link uk.co.real_logic.sbe.PrimitiveType} objects. Each encoding node contains encodedLength, offset, byte order,
 * and {@link Encoding}. Entities relevant to the encoding such as fields, messages, repeating groups, etc. are
 * encapsulated in the list as nodes themselves. Although, they will in most cases never be serialized. The boundaries
 * of these entities are delimited by BEGIN and END {@link Signal} values in the node {@link Encoding}.
 * A list structure like this allows for each concatenation of encodings as well as easy traversal.
 * <p>
 * An example encoding of a message headerStructure might be like this.
 * <ul>
 * <li>Token 0 - Signal = BEGIN_MESSAGE, schemaId = 100</li>
 * <li>Token 1 - Signal = BEGIN_FIELD, schemaId = 25</li>
 * <li>Token 2 - Signal = ENCODING, PrimitiveType = uint32, encodedLength = 4, offset = 0</li>
 * <li>Token 3 - Signal = END_FIELD</li>
 * <li>Token 4 - Signal = END_MESSAGE</li>
 * </ul>
 */
public class Token
{
    /**
     * Invalid ID value.
     */
    public static final int INVALID_ID = -1;

    /**
     * Length not determined
     */
    public static final int VARIABLE_LENGTH = -1;

    private final Signal signal;
    private final String name;
    private final String referencedName;
    private final String description;
    private final String packageName;
    private final int id;
    private final int version;
    private final int deprecated;
    private int encodedLength;
    private final int offset;
    private int componentTokenCount;
    private final Encoding encoding;

    /**
     * Construct an {@link Token} by providing values for all fields.
     *
     * @param signal              for the token role.
     * @param name                of the token in the message.
     * @param referencedName      of the type when created from a ref in a composite.
     * @param description         of what the token is for.
     * @param packageName         of the token in the message. Use null, except for BEGIN_MESSAGE tokens for types that
     *                            require an explicit package.
     * @param id                  as the identifier in the message declaration.
     * @param version             application within the template.
     * @param deprecated          as of this version.
     * @param encodedLength       of the component part.
     * @param offset              in the underlying message as octets.
     * @param componentTokenCount number of tokens in this component.
     * @param encoding            of the primitive field.
     */
    public Token(
        final Signal signal,
        final String name,
        final String referencedName,
        final String description,
        final String packageName,
        final int id,
        final int version,
        final int deprecated,
        final int encodedLength,
        final int offset,
        final int componentTokenCount,
        final Encoding encoding)
    {
        Verify.notNull(signal, "signal");
        Verify.notNull(name, "name");
        Verify.notNull(encoding, "encoding");

        this.signal = signal;
        this.name = name;
        this.referencedName = referencedName;
        this.description = description;
        this.packageName = packageName;
        this.id = id;
        this.version = version;
        this.deprecated = deprecated;
        this.encodedLength = encodedLength;
        this.offset = offset;
        this.componentTokenCount = componentTokenCount;
        this.encoding = encoding;
    }

    /**
     * Signal the role of this token.
     *
     * @return the {@link Signal} for the token.
     */
    public Signal signal()
    {
        return signal;
    }

    /**
     * Return the name of the token
     *
     * @return name of the token
     */
    public String name()
    {
        return name;
    }

    /**
     * Return the packageName of the token
     *
     * @return packageName of the token or null, if it was not set explicitly.
     */
    public String packageName()
    {
        return packageName;
    }

    /**
     * Get the name of the type when this is from a reference.
     *
     * @return the name of the type when this is from a reference.
     */
    public String referencedName()
    {
        return referencedName;
    }

    /**
     * Description for what the token is to be used for.
     *
     * @return description for what the token is to be used for.
     */
    public String description()
    {
        return description;
    }

    /**
     * Return the ID of the token assigned by the specification
     *
     * @return ID of the token assigned by the specification
     */
    public int id()
    {
        return id;
    }

    /**
     * The version context for this token. This is the schema version in which the type was introduced.
     *
     * @return version for this type.
     */
    public int version()
    {
        return version;
    }

    /**
     * The version in which this context was deprecated.
     *
     * @return the version in which this context was deprecated.
     */
    public int deprecated()
    {
        return deprecated;
    }

    /**
     * Get the name of the type that should be applied in context.
     *
     * @return the name of the type that should be applied in context.
     */
    public String applicableTypeName()
    {
        return null == referencedName ? name : referencedName;
    }

    /**
     * The encodedLength of this token in bytes.
     *
     * @return the encodedLength of this node. A value of 0 means the node has no encodedLength when encoded.
     * A value of {@link Token#VARIABLE_LENGTH} means this node represents a variable length field.
     */
    public int encodedLength()
    {
        return encodedLength;
    }

    /**
     * Set the encoded length for this node. See {@link #encodedLength()}.
     *
     * @param encodedLength that is overriding existing value.
     */
    public void encodedLength(final int encodedLength)
    {
        this.encodedLength = encodedLength;
    }

    /**
     * The number of encoded primitives in this type.
     *
     * @return number of encoded primitives in this type.
     */
    public int arrayLength()
    {
        if (null == encoding.primitiveType() || 0 == encodedLength)
        {
            return 0;
        }

        return encodedLength / encoding.primitiveType().size();
    }

    /**
     * Match which approach to take based on the length of the token. If length is zero then an empty
     * {@link String} is returned.
     *
     * @param one  to be used when length is one.
     * @param many to be used when length is greater than one.
     * @return the {@link CharSequence} representing the token depending on the length.
     */
    public CharSequence matchOnLength(final Supplier<CharSequence> one, final Supplier<CharSequence> many)
    {
        final int arrayLength = arrayLength();

        if (arrayLength == 1)
        {
            return one.get();
        }
        else if (arrayLength > 1)
        {
            return many.get();
        }

        return "";
    }

    /**
     * The offset for this token in the message.
     *
     * @return the offset of this Token. A value of 0 means the node has no relevant offset. A value of
     * {@link Token#VARIABLE_LENGTH} means this node's true offset is dependent on variable length
     * fields ahead of it in the encoding.
     */
    public int offset()
    {
        return offset;
    }

    /**
     * The number of tokens that make up this component.
     *
     * @return the number of tokens that make up this component.
     */
    public int componentTokenCount()
    {
        return componentTokenCount;
    }

    /**
     * Set the number of tokens this component has.
     *
     * @param componentTokenCount the number of tokens this component has.
     */
    public void componentTokenCount(final int componentTokenCount)
    {
        this.componentTokenCount = componentTokenCount;
    }

    /**
     * Return the {@link Encoding} of the {@link Token}.
     *
     * @return encoding of the {@link Token}
     */
    public Encoding encoding()
    {
        return encoding;
    }

    /**
     * Is the encoding presence is a constant or not?
     *
     * @return true if the encoding presence is a constant or false if not.
     */
    public boolean isConstantEncoding()
    {
        return encoding.presence() == CONSTANT;
    }

    /**
     * Is the encoding presence is optional or not?
     *
     * @return true if the encoding presence is optional or false if not.
     */
    public boolean isOptionalEncoding()
    {
        return encoding.presence() == OPTIONAL;
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        return "Token{" +
            "signal=" + signal +
            ", name='" + name + '\'' +
            ", referencedName='" + referencedName + '\'' +
            ", description='" + description + '\'' +
            ", packageName='" + packageName + '\'' +
            ", id=" + id +
            ", version=" + version +
            ", deprecated=" + deprecated +
            ", encodedLength=" + encodedLength +
            ", offset=" + offset +
            ", componentTokenCount=" + componentTokenCount +
            ", encoding=" + encoding +
            '}';
    }

    /**
     * Builder for {@link Token} which can simplify construction.
     */
    public static class Builder
    {
        private Signal signal;
        private String name;
        private String packageName = null;
        private String referencedName;
        private String description;
        private int id = INVALID_ID;
        private int version = 0;
        private int deprecated = 0;
        private int size = 0;
        private int offset = 0;
        private int componentTokenCount = 1;
        private Encoding encoding = new Encoding();

        /**
         * Signal for the Token.
         *
         * @param signal for the Token.
         * @return this for a fluent API.
         */
        public Builder signal(final Signal signal)
        {
            this.signal = signal;
            return this;
        }

        /**
         * Name for the Token.
         *
         * @param name for the Token.
         * @return this for a fluent API.
         */
        public Builder name(final String name)
        {
            this.name = name;
            return this;
        }

        /**
         * Package name for the Token. Default is null. Use for BEGIN_MESSAGE tokens for types that require an explicit
         * package.
         *
         * @param packageName for the Token.
         * @return this for a fluent API.
         */
        public Builder packageName(final String packageName)
        {
            this.packageName = packageName;
            return this;
        }

        /**
         * Referenced type name for the Token.
         *
         * @param referencedName for the Token.
         * @return this for a fluent API.
         */
        public Builder referencedName(final String referencedName)
        {
            this.referencedName = referencedName;
            return this;
        }

        /**
         * Description attribute for the Token.
         *
         * @param description for the Token.
         * @return this for a fluent API.
         */
        public Builder description(final String description)
        {
            this.description = description;
            return this;
        }

        /**
         * ID attribute for the Token.
         *
         * @param id for the Token.
         * @return this for a fluent API.
         */
        public Builder id(final int id)
        {
            this.id = id;
            return this;
        }

        /**
         * Version attribute value for the Token.
         *
         * @param version for the Token.
         * @return this for a fluent API.
         */
        public Builder version(final int version)
        {
            this.version = version;
            return this;
        }

        /**
         * Deprecated version attribute for the Token.
         *
         * @param deprecated version for the Token.
         * @return this for a fluent API.
         */
        public Builder deprecated(final int deprecated)
        {
            this.deprecated = deprecated;
            return this;
        }

        /**
         * Size of the type for the Token.
         *
         * @param size for the Token.
         * @return this for a fluent API.
         */
        public Builder size(final int size)
        {
            this.size = size;
            return this;
        }

        /**
         * Offset in the message for the Token.
         *
         * @param offset for the Token.
         * @return this for a fluent API.
         */
        public Builder offset(final int offset)
        {
            this.offset = offset;
            return this;
        }

        /**
         * Count of tokens in the component.
         *
         * @param componentTokenCount for the component.
         * @return this for a fluent API.
         */
        public Builder componentTokenCount(final int componentTokenCount)
        {
            this.componentTokenCount = componentTokenCount;
            return this;
        }

        /**
         * Encoding type for the Token.
         *
         * @param encoding for the Token.
         * @return this for a fluent API.
         */
        public Builder encoding(final Encoding encoding)
        {
            this.encoding = encoding;
            return this;
        }

        /**
         * Build a new Token based on the values.
         *
         * @return a new Token based on the values.
         */
        public Token build()
        {
            return new Token(
                signal,
                name,
                referencedName,
                description,
                packageName,
                id,
                version,
                deprecated,
                size,
                offset,
                componentTokenCount,
                encoding);
        }
    }
}
