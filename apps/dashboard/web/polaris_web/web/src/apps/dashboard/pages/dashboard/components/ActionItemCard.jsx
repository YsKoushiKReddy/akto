import { Avatar, Badge, Box, Button, Card, Divider, HorizontalStack, Icon, Text, VerticalStack, Popover, OptionList, Tag } from '@shopify/polaris'
import React, { useState } from 'react'
import { TeamMajor, ToolsMajor, EmailMajor } from "@shopify/polaris-icons"
import TooltipText from '../../../components/shared/TooltipText'

function ActionItemCard(props) {
    const {cardObj, onButtonClick } = props ;
    
    return (
        <div
            onClick={e => {
                // Prevent flyout if clicking on assign button, popover, or tag
                if (
                    e.target.closest('.Polaris-Button') ||
                    e.target.closest('.Polaris-Popover') ||
                    e.target.closest('.Polaris-Tag')
                ) {
                    return;
                }
                onButtonClick(cardObj);
            }}
            style={{cursor: 'pointer'}}
        >
        <Card padding={"5"}>
            <VerticalStack gap={"3"}>
                <Box width='30px'>
                <Badge status="critical-strong-experimental">P0</Badge>
                </Box>
                <Box maxWidth="220px">
                    <TooltipText tooltip={"lmfao bro this is isnaen dedejde onde on"} text={"lmfaoooo jdnew jnewd wedwed wednjkwed wedjkn w wedjn edn"} textProps={{variant: 'headingSm'}} />
                    <TooltipText tooltip={"lmfao"} text={"lmfaoooo"} textProps={{variant: 'bodyMd', color: 'subdued'}} />
                </Box>
                <HorizontalStack gap={"2"}>
                    <HorizontalStack gap={"1"}>
                        <Box><Icon source={TeamMajor} color="subdued" /></Box>
                        <Text variant='bodyMd'>Platform</Text>
                    </HorizontalStack>
                    <HorizontalStack gap={"1"}>
                        <Box><Icon source={ToolsMajor} color="subdued" /></Box>
                        <Text variant='bodyMd'>Low</Text>
                    </HorizontalStack>
                </HorizontalStack>
                <Divider />
                <HorizontalStack gap={"3"} align="space-between" wrap={false}>
                    <Box className="action-item-card-actions">
                        <HorizontalStack gap={"2"}>
                            <button className="Polaris-Modal-CloseButton" onClick={() => {}}><Box><Icon color="subdued" source={EmailMajor} /></Box></button>
                            <button className="Polaris-Modal-CloseButton" onClick={() => {}}><Box className='reduce-size'><Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg"/></Box></button>
                        </HorizontalStack>
                    </Box>
                    <Box>
                        {/* TODO: Re-enable assign task functionality in future iteration */}
                        {/* {assignedUser ? (
                            <Tag onRemove={() => setSelectedUser([])}>
                                {assignedUser.label}
                            </Tag>
                        ) : (
                            <Popover
                                active={popoverActive}
                                activator={activator}
                                onClose={() => setPopoverActive(false)}
                                autofocusTarget="first-node"
                            >
                                <OptionList
                                    title="Assign to"
                                    onChange={handleUserSelect}
                                    options={users}
                                    selected={selectedUser}
                                />
                            </Popover>
                        )} */}
                    </Box>
                </HorizontalStack>
            </VerticalStack>
        </Card>
        </div>
    )
}

export default ActionItemCard