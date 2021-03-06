package com.github.fge.jsonschema.syntax.checkers.hyperschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.NodeType;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.syntax.checkers.AbstractSyntaxChecker;
import com.github.fge.jsonschema.syntax.checkers.SyntaxChecker;
import com.github.fge.jsonschema.tree.SchemaTree;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;

import java.util.Collection;
import java.util.Set;

import static com.github.fge.jsonschema.messages.SyntaxMessages.*;

public final class MediaSyntaxChecker
    extends AbstractSyntaxChecker
{
    private static final String BINARY_ENCODING_FIELDNAME = "binaryEncoding";
    private static final String TYPE_FIELDNAME = "type";

    // FIXME: INCOMPLETE: excludes "x-token" and "ietf-token"
    private static final Set<String> BINARY_ENCODINGS = ImmutableSet.of(
        "7bit", "8bit", "binary", "quoted-printable", "base64"
    );

    private static final SyntaxChecker INSTANCE = new MediaSyntaxChecker();

    private MediaSyntaxChecker()
    {
        super("media", NodeType.OBJECT);
    }

    public static SyntaxChecker getInstance()
    {
        return INSTANCE;
    }

    @Override
    protected void checkValue(final Collection<JsonPointer> pointers,
        final ProcessingReport report, final SchemaTree tree)
        throws ProcessingException
    {
        final JsonNode node = getNode(tree);
        JsonNode subNode;
        NodeType type;
        String value;

        subNode = node.path(BINARY_ENCODING_FIELDNAME);
        if (!subNode.isMissingNode()) {
            type = NodeType.getNodeType(subNode);
            value = subNode.textValue();
            if (value == null)
                report.error(newMsg(tree, HS_MEDIA_INVALID_ENCODING_TYPE)
                    .put("expected", NodeType.STRING).put("found", type));
            else if (!BINARY_ENCODINGS.contains(value.toLowerCase()))
                report.error(newMsg(tree, HS_MEDIA_INVALID_ENCODING)
                    .put("value", value));
        }

        subNode = node.path(TYPE_FIELDNAME);
        if (subNode.isMissingNode())
            return;
        type = NodeType.getNodeType(subNode);
        if (type != NodeType.STRING) {
            report.error(newMsg(tree, HS_MEDIA_INVALID_TYPE_TYPE)
                .put("expected", NodeType.STRING).put("found", type));
            return;
        }
        value = subNode.textValue();
        try {
            MediaType.parse(value);
        } catch (IllegalArgumentException ignored) {
            report.error(newMsg(tree, HS_MEDIA_INVALID_TYPE)
                .put("value", value));
        }
    }
}
