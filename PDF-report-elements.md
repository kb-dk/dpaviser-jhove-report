# These cells should be included in the PDF lines:

## From top level //repInfo:

* size
* format
* version
* status
* profiles/profile

## From `//properties/property[name/text()='Info']:`

* Title
* Producer
* CreationDate
* ModDate

## Images

Number of JPEG Images

    /j:jhove/repInfo/properties/property/values/property[name/text()='Images']/values/property[name/text()='Image']/values/property[name/text()='NisoImageMetadata']/values/value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='JPEG']

Number of Uncompressed Images

    /j:jhove/repInfo/properties/property/values/property[name/text()='Images']/values/property[name/text()='Image']/values/property[name/text()='NisoImageMetadata']/values/value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='Uncompressed']

Number of FlateDecode Images

    /j:jhove/repInfo/properties/property/values/property[name/text()='Images']/values/property[name/text()='Image']/values/property[name/text()='Filter']/values/value[text()='FlateDecode']

Number of other images

    (Everything under /j:jhove/repInfo/properties/property/values/property[name/text()='Images'] not matched above)

## Fonts

Number of Fonts, type0

    /j:jhove/repInfo/properties/property/values/property[name/text()='Fonts']/values/property[name/text()='Type0']/values/property[name/text()='Font']

Number of Fonts, type1

    /j:jhove/repInfo/properties/property/values/property[name/text()='Fonts']/values/property[name/text()='Type1']/values/property[name/text()='Font']

Number of Fonts, truetype

    /j:jhove/repInfo/properties/property/values/property[name/text()='Fonts']/values/property[name/text()='TrueType0']/values/property[name/text()='Font']

Number of Fonts, CIDFontType0

    /j:jhove/repInfo/properties/property/values/property[name/text()='Fonts']/values/property[name/text()='CIDFontType0']/values/property[name/text()='Font']

Number of other fonts

    (Everything under /j:jhove/repInfo/properties/property/values/property[name/text()='Fonts'] not matched above)

Number of fonts that are not embedded

    /j:jhove/j:repInfo/j:properties/j:property/j:values/j:property[j:name/text()='Fonts']/j:values/j:property/j:values/j:property[j:name/text()='Font'][not(j:values/j:property[j:name/text()='FontDescriptor']/j:values/j:property/j:name[starts-with(text(),'FontFile')])]

## Pages

    /j:jhove/repInfo/properties/property/values/property[name/text()='Pages']/values/property[name/text()='Page']
