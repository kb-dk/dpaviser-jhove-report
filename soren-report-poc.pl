#!/usr/bin/env perl -l
use XML::XPath;
use XML::XPath::XMLParser;

while (<>) {
    chomp;
    if (m!/([A-Z]{3}[^/]+)[A-Z](\d.)#\d\d\d\d\.pdf$!) {
	$seen{"$1?$2"}++;
    } elsif (m!\.xml$!) {
	# http://mkweb.bcgsc.ca/intranet/perlbook/pxml/ch08_02.htm
	my $xpath = XML::XPath->new(filename => $_);
	my $section = $xpath->find('string(//Property[@FormalName="PSNA"]/@Value)')->to_literal();
	my $pdf = $xpath->find('//media[@media-type="PDF"]/media-reference[@exists=1][1]/text()')->to_literal();
	$pdf =~ s/^\s+|\s+$//g; # trim spaces
	if ($pdf =~ m![/\\]([A-Z]{3}[^/]+)[A-Z](\d.)#\d\d\d\d\.pdf$!) {
	    $key = "$1?$2";
	    $section_for{$key} = $section;
	    # print "$key - $section";
	} else {
	    # some XML files do not have PDF link.
	}
    }
}
foreach my $key (sort keys %seen) {
    print "$key\t$seen{$key} sider\t$section_for{$key}";
}
