<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:j="http://hul.harvard.edu/ois/xml/ns/jhove"
                xmlns:mix="http://www.loc.gov/mix/v20">

    <xsl:output omit-xml-declaration="yes" indent="yes"/>

    <xsl:template match="/">
        <rows>
            <xsl:apply-templates/>
        </rows>
    </xsl:template>

    <xsl:template match="/j:jhove/j:repInfo">
        <row>
            <cell>
                <xsl:value-of select="@uri"/>
            </cell>
            <xsl:variable name="JPEG" select="count(j:properties/j:property/j:values/j:property[j:name='Images']/j:values/j:property[j:name='Image']/j:values/j:property[j:name='NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='JPEG'])"/>
            <cell><xsl:value-of select="$JPEG"/></cell>
            <xsl:variable name="UNCOMPRESSED" select="count(j:properties/j:property/j:values/j:property[j:name='Images']/j:values/j:property[j:name='Image']/j:values/j:property[j:name='NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='Uncompressed'])"/>
            <cell><xsl:value-of select="$UNCOMPRESSED"/></cell>
            <xsl:variable name="FLATEDECODE" select="count(j:properties/j:property/j:values/j:property[j:name='Images']/j:values/j:property[j:name='Image']/j:values/j:property[j:name='Filter']/j:values/j:value[text()='FlateDecode'])"/>
            <cell><xsl:value-of select="$FLATEDECODE"/></cell>
            <cell>-1</cell><!-- how to? -->
            <xsl:variable name="TYPE0" select="count(j:properties/j:property/j:values/j:property[j:name='Fonts']/j:values/j:property[j:name='Type0']/j:values/j:property[j:name='Font'])"/>
            <cell><xsl:value-of select="$TYPE0"/></cell>
            <xsl:variable name="TYPE1" select="count(j:properties/j:property/j:values/j:property[j:name='Fonts']/j:values/j:property[j:name='Type1']/j:values/j:property[j:name='Font'])"/>
            <cell><xsl:value-of select="$TYPE1"/></cell>
            <xsl:variable name="TRUETYPE" select="count(j:properties/j:property/j:values/j:property[j:name='Fonts']/j:values/j:property[j:name='TrueType']/j:values/j:property[j:name='Font'])"/>
            <cell><xsl:value-of select="$TRUETYPE"/></cell>
            <xsl:variable name="ALLFONTS" select="count(j:properties/j:property/j:values/j:property[j:name='Fonts']/j:values/j:property/j:values/j:property[j:name='Font'])"/>
            <cell><xsl:value-of select="$ALLFONTS - $TYPE0 - $TYPE1 - $TRUETYPE"/></cell>
            <cell><xsl:value-of select="count(j:properties/j:property/j:values/j:property[j:name='Pages']/j:values/j:property[j:name='Page'])" /></cell>
        </row>
    </xsl:template>

    <xsl:template match="text()"/>

</xsl:stylesheet>