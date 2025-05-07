import { Autocomplete, Avatar, Icon, Link, TextContainer } from '@shopify/polaris';
import { SearchMinor, ChevronDownMinor } from '@shopify/polaris-icons';
import React, { useState, useCallback, useEffect } from 'react';
import func from "@/util/func";

function DropdownSearchWithDisabled(props) {
    const id = props.id ? props.id : "dropdown-search"

    const {
        disabled,
        label,
        placeholder,
        optionsList,
        setSelected,
        value,
        avatarIcon,
        preSelected,
        allowMultiple,
        itemName,
        dropdownSearchKey,
        isNested,
        sliceMaxVal,
        showSelectedItemLabels=false,
        searchDisable=false,
        disabledOptions=[]
    } = props

    // Create a modified options list with disabled property
    const deselectedOptions = React.useMemo(() => {
        return optionsList.map(option => ({
            ...option,
            disabled: disabledOptions.includes(option.value)
        }));
    }, [optionsList, disabledOptions]);

    const [selectedOptions, setSelectedOptions] = useState(preSelected ? preSelected : []);
    const [inputValue, setInputValue] = useState(value ? value : undefined);
    const [options, setOptions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [checked, setChecked] = useState(false);

    // Update input value when value prop changes
    useEffect(() => {
        if(value !== undefined && value !== inputValue){
            setInputValue(value);
        }
    }, [value, inputValue]);

    // Update selected options when preSelected prop changes
    useEffect(() => {
        if(preSelected !== undefined && !func.deepComparison(selectedOptions, preSelected)){
            setSelectedOptions([...preSelected]);
        }
    }, [preSelected, selectedOptions]);

    // Initialize and update options when deselectedOptions change
    useEffect(() => {
        setOptions(deselectedOptions);
    }, [deselectedOptions]);

    // Update checked state for select all functionality
    useEffect(() => {
        if(allowMultiple){
            let totalItems = deselectedOptions.length
            if(isNested){
                totalItems = 0
                deselectedOptions.forEach((opt) => {
                    totalItems += opt.options.length
                })
            }
            setChecked(preSelected.length === totalItems);
        }
    }, [allowMultiple, deselectedOptions, isNested, preSelected]);

    const updateText = useCallback(
        (value) => {
            setInputValue(value);

            if (!loading) {
                setLoading(true);
            }

            const defaultSliceValue = sliceMaxVal || 20

            setTimeout(() => {
                if (value === '' && selectedOptions.length === 0) {
                    const options = deselectedOptions.slice(0, defaultSliceValue);
                    const title = deselectedOptions.length != defaultSliceValue && options.length >= defaultSliceValue
                        ? `Showing ${options.length} result${func.addPlurality(options.length)} only. (type more to refine results)`
                        : "Showing all results";
                    const nestedOptions = [{
                        title: title,
                        options: options
                    }]
                    setOptions(nestedOptions);
                    setLoading(false);
                    return;
                }
                const filterRegex = new RegExp(value, 'i');
                const searchKey = dropdownSearchKey ? dropdownSearchKey : "label"
                let resultOptions = []
                if(isNested){
                    deselectedOptions.forEach((opt) => {
                        const options = opt.options.filter((option) =>
                          option[searchKey].match(filterRegex),
                        );

                        resultOptions.push({
                          title: opt.title,
                          options,
                        });
                      });
                }else{
                    resultOptions = deselectedOptions.filter((option) =>
                        option[searchKey].match(filterRegex)
                    ).slice(0, defaultSliceValue);

                    const title = deselectedOptions.length !== defaultSliceValue && resultOptions.length >= defaultSliceValue
                        ? `Showing ${resultOptions.length} result${func.addPlurality(resultOptions.length)} only. (type more to refine results)`
                        : "Showing all results";

                    resultOptions = [{
                        title: title,
                        options: resultOptions
                    }]
                }
                setOptions(resultOptions);
                setLoading(false);
            }, 300);
        },
        [deselectedOptions, loading, selectedOptions, disabledOptions],
    );

    const handleFocusEvent = () => {
        updateText('');
    }

    const updateSelection = useCallback(
        (selected) => {
            // Filter out any disabled options that might have been selected
            const filteredSelected = selected.filter(
                selectedItem => !disabledOptions.includes(selectedItem)
            );

            const selectedText = filteredSelected.map((selectedItem) => {
                const matchedOption = optionsList.find((option) => {
                    if (typeof option.value === "string")
                        return option.value.match(selectedItem);
                    else
                        return option.value === selectedItem
                });
                return matchedOption && matchedOption.label;
            });

            setSelectedOptions([...filteredSelected]);

            if (avatarIcon) {
                setInputValue(filteredSelected[0])
            } else if (allowMultiple) {
                if(showSelectedItemLabels) {
                    if(selectedText.length === optionsList.length) setInputValue("All items selected");
                    else setInputValue(func.getSelectedItemsText(selectedText))
                }
                else setInputValue(`${filteredSelected.length} ${itemName ? itemName : "item"}${filteredSelected.length == 1 ? "" : "s"} selected`)
            }
            else {
                setInputValue(selectedText[0] || '');
            }

            if (allowMultiple) {
                setSelected(filteredSelected);
            } else {
                setSelected(filteredSelected[0])
            }
        },
        [optionsList, disabledOptions, allowMultiple, showSelectedItemLabels, itemName, avatarIcon, setSelected],
    );

    const selectAllFunc = () => {
        if(!checked){
            let valueArr = []
            if(isNested){
                deselectedOptions.forEach((opt) => {
                    opt.options.forEach((option) => {
                        if (!option.disabled) {
                            valueArr.push(option.value);
                        }
                    });
                })
            }else{
                deselectedOptions.forEach((opt) => {
                    if (!opt.disabled) {
                        valueArr.push(opt.value);
                    }
                });
            }
            updateSelection(valueArr)
            setChecked(true)
        }else{
            setChecked(false)
            updateSelection([])
        }
    }

    const textField = (
        <Autocomplete.TextField
            id={id}
            disabled={disabled}
            {...(!searchDisable ? {onChange:updateText}:{})}
            label={label}
            value={inputValue}
            {...(!searchDisable ? {
                prefix: (
                    <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                        <Icon source={SearchMinor} color="base" />
                        {avatarIcon && avatarIcon.length > 0 ? <Avatar customer size="extraSmall" name={avatarIcon} source={avatarIcon} /> : null}
                    </div>
                )
            } : {})}
            suffix={<Icon source={ChevronDownMinor} color="base" />}
            placeholder={placeholder}
            autoComplete="off"
            {...(!searchDisable? {onFocus:handleFocusEvent}: {})}
        />
    );

    const showSelectAll = (allowMultiple && optionsList.length > 5)
    const checkboxLabel = checked ? <Link removeUnderline>Deselect all</Link> : <Link removeUnderline>Select all</Link>

    const emptyState = (
        <React.Fragment>
            <Icon source={SearchMinor} />
            <div style={{ textAlign: 'center' }}>
                <TextContainer>Could not find any results</TextContainer>
            </div>
        </React.Fragment>
    );

    // Custom renderOption function to style disabled options
    const renderOption = (option, isSelected) => {
        const { label, value, disabled } = option;

        if (disabled) {
            return (
                <div
                    style={{
                        opacity: 0.5,
                        cursor: 'not-allowed',
                        backgroundColor: '#f4f6f8',
                        padding: '8px',
                        color: '#637381'
                    }}
                >
                    {label} (already selected)
                </div>
            );
        }

        return undefined; // Return undefined to use default rendering for enabled options
    };

    return (
        <Autocomplete
            {...(allowMultiple ? {allowMultiple:true} : {} )}
            options={options.slice(0,sliceMaxVal || 20)}
            selected={selectedOptions}
            onSelect={updateSelection}
            emptyState={emptyState}
            loading={loading}
            textField={textField}
            preferredPosition='below'
            {...(showSelectAll ? {actionBefore:{
                content: checkboxLabel,
                onAction: () => selectAllFunc(),
            }} : {})}
            renderOption={renderOption}
        >
        </Autocomplete>
    );
}

export default DropdownSearchWithDisabled;
