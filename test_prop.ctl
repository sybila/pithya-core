high = E2F1 > 4 && E2F1 < 7.5
low = E2F1 > 0.5 && E2F1 < 2.5

lowStable = AG low
highStable = AG high

bistab = EF lowStable && EF highStable